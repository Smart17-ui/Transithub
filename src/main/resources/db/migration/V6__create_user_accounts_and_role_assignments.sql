-- Identity and access (ADR-0004, ADR-0016; Release 1 spec section 3).
--
-- Cognito proves who someone is; these tables decide what they may do.
-- A user is linked to Cognito by the access token's "sub" claim. Job titles
-- grant nothing: only role assignments do, each scoped to the platform
-- (company_id IS NULL) or to one company.

CREATE TABLE user_account (
    id                UUID PRIMARY KEY,
    identity_subject  VARCHAR(255) NOT NULL,   -- Cognito "sub"
    email             VARCHAR(254),
    display_name      VARCHAR(200) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_user_account_identity_subject UNIQUE (identity_subject),
    CONSTRAINT chk_user_account_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE role_assignment (
    id             UUID PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES user_account (id),
    role_code      VARCHAR(60)  NOT NULL,
    company_id     UUID         REFERENCES companies (id),   -- NULL = platform scope
    valid_from     TIMESTAMPTZ  NOT NULL,
    valid_until    TIMESTAMPTZ,
    granted_by     VARCHAR(100) NOT NULL,
    granted_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_by     VARCHAR(100),
    revoke_reason  VARCHAR(2000),

    CONSTRAINT chk_role_assignment_period CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT chk_role_assignment_revocation CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);

-- A user holds a given role in a given scope at most once at a time.
CREATE UNIQUE INDEX uq_role_assignment_active
    ON role_assignment (user_id, role_code, coalesce(company_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE revoked_at IS NULL;

CREATE INDEX ix_role_assignment_company ON role_assignment (company_id) WHERE company_id IS NOT NULL;

-- Grants: no DELETE. Accounts are disabled, and assignments revoked, never removed.
GRANT SELECT, INSERT, UPDATE ON user_account    TO transithub_runtime;
GRANT SELECT, INSERT, UPDATE ON role_assignment TO transithub_runtime;

-- Row-level security ---------------------------------------------------------
ALTER TABLE user_account    ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_assignment ENABLE ROW LEVEL SECURITY;

-- Platform staff see all users; a company sees the users assigned to it.
CREATE POLICY user_account_read ON user_account
    FOR SELECT
    USING (app_is_platform_scope()
           OR EXISTS (SELECT 1 FROM role_assignment ra
                       WHERE ra.user_id = user_account.id
                         AND ra.company_id = app_current_company_id()));

CREATE POLICY role_assignment_read ON role_assignment
    FOR SELECT
    USING (app_is_platform_scope() OR company_id = app_current_company_id());

-- Release 1: only platform staff manage users and roles. Company
-- administrators managing their own users arrive in Release 2.
CREATE POLICY user_account_write ON user_account
    FOR INSERT WITH CHECK (app_is_platform_scope());
CREATE POLICY user_account_update ON user_account
    FOR UPDATE USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY role_assignment_write ON role_assignment
    FOR INSERT WITH CHECK (app_is_platform_scope());
CREATE POLICY role_assignment_update ON role_assignment
    FOR UPDATE USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());

-- Principal lookup -----------------------------------------------------------
-- At the start of a request the tenant scope is not yet known (it is derived
-- from these very rows), so RLS would hide them. This function runs with the
-- owner's rights and returns ONLY the calling user's own active assignments,
-- looked up by the subject from their verified token.
CREATE FUNCTION app_principal_lookup(p_subject TEXT)
RETURNS TABLE (user_id UUID, user_status VARCHAR, role_code VARCHAR, company_id UUID)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT u.id, u.status, ra.role_code, ra.company_id
      FROM user_account u
      LEFT JOIN role_assignment ra
             ON ra.user_id = u.id
            AND ra.revoked_at IS NULL
            AND ra.valid_from <= now()
            AND (ra.valid_until IS NULL OR ra.valid_until > now())
     WHERE u.identity_subject = p_subject
$$;

REVOKE ALL ON FUNCTION app_principal_lookup(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_principal_lookup(TEXT) TO transithub_runtime;

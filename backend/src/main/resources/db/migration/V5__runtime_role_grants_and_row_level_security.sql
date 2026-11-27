-- Tenant isolation, database layer (ADR-0003, AC-R1-X-01).
--
-- The application connects as transithub_runtime, which owns nothing, so
-- these policies always apply to it. Each connection carries two settings,
-- written by TenantAwareDataSource from the request's tenant scope:
--
--   app.scope       'platform' (platform staff: all companies)
--                   'company'  (one company's users)
--                   ''         (no scope: sees no tenant rows at all)
--   app.company_id  the company's id when app.scope = 'company'
--
-- Every new tenant table must: GRANT to transithub_runtime explicitly, ENABLE
-- ROW LEVEL SECURITY and add a policy. TenantIsolationSchemaTest fails the
-- build if a table is missed.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'transithub_runtime') THEN
        RAISE EXCEPTION 'Role transithub_runtime does not exist. Run scripts/db/create-runtime-role.sql as a superuser first.';
    END IF;

    EXECUTE format('GRANT CONNECT ON DATABASE %I TO transithub_runtime', current_database());
END
$$;

GRANT USAGE ON SCHEMA public TO transithub_runtime;

-- Helper functions, so every policy reads the settings the same way.
-- missing_ok = true: an unset setting reads as NULL, which matches nothing.
CREATE FUNCTION app_is_platform_scope() RETURNS boolean
LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.scope', true), '') = 'platform'
$$;

CREATE FUNCTION app_current_company_id() RETURNS uuid
LANGUAGE sql STABLE AS $$
    SELECT CASE
        WHEN coalesce(current_setting('app.scope', true), '') = 'company'
        THEN nullif(current_setting('app.company_id', true), '')::uuid
    END
$$;

GRANT EXECUTE ON FUNCTION app_is_platform_scope() TO transithub_runtime;
GRANT EXECUTE ON FUNCTION app_current_company_id() TO transithub_runtime;

-- companies ------------------------------------------------------------------
-- Companies are never deleted: no DELETE grant.
GRANT SELECT, INSERT, UPDATE ON companies TO transithub_runtime;

ALTER TABLE companies ENABLE ROW LEVEL SECURITY;

-- Platform staff see every company; a company's users see only their own.
CREATE POLICY companies_read ON companies
    FOR SELECT
    USING (app_is_platform_scope() OR id = app_current_company_id());

-- Only platform staff create and change company records in Release 1
-- (companies are established by onboarding approval).
CREATE POLICY companies_insert ON companies
    FOR INSERT
    WITH CHECK (app_is_platform_scope());

CREATE POLICY companies_update ON companies
    FOR UPDATE
    USING (app_is_platform_scope())
    WITH CHECK (app_is_platform_scope());

-- audit_event ----------------------------------------------------------------
-- Append-only: SELECT and INSERT only (the triggers from V2 also block
-- UPDATE, DELETE and TRUNCATE for every role).
GRANT SELECT, INSERT ON audit_event TO transithub_runtime;

ALTER TABLE audit_event ENABLE ROW LEVEL SECURITY;

CREATE POLICY audit_event_read ON audit_event
    FOR SELECT
    USING (app_is_platform_scope() OR company_id = app_current_company_id());

-- A company-scoped action may only write events about its own company.
CREATE POLICY audit_event_insert ON audit_event
    FOR INSERT
    WITH CHECK (app_is_platform_scope() OR company_id = app_current_company_id());

-- idempotency_record ---------------------------------------------------------
-- Technical table, not tenant data: no row-level security. Keys are scoped
-- by method and path, and from Release 1 step 2 also by the caller.
GRANT SELECT, INSERT, UPDATE, DELETE ON idempotency_record TO transithub_runtime;

-- First Company Administrator invitation (Release 1 spec 5.7;
-- AC-R1-EST-04..06; UC-01.01-16). Platform data.
--
-- The invitation link carries a random token. Only its SHA-256 hash is
-- stored, so someone reading this table cannot use the links.

CREATE TABLE company_invitation (
    id                 UUID PRIMARY KEY,
    company_id         UUID          NOT NULL REFERENCES companies (id),
    registration_id    UUID          NOT NULL REFERENCES company_registration (id),
    representative_id  UUID          NOT NULL REFERENCES registration_representative (id),
    recipient_name     VARCHAR(200)  NOT NULL,
    recipient_email    VARCHAR(254),
    recipient_phone    VARCHAR(30),
    token_hash         CHAR(64)      NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    created_by         VARCHAR(100)  NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    expires_at         TIMESTAMPTZ   NOT NULL,
    accepted_by_user   UUID,
    accepted_at        TIMESTAMPTZ,
    revoked_by         VARCHAR(100),
    revoked_at         TIMESTAMPTZ,
    reissue_of         UUID REFERENCES company_invitation (id),

    CONSTRAINT uq_company_invitation_token UNIQUE (token_hash),
    CONSTRAINT chk_company_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED')),
    CONSTRAINT chk_company_invitation_accepted CHECK ((status = 'ACCEPTED') = (accepted_at IS NOT NULL)),
    CONSTRAINT chk_company_invitation_revoked CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL))
);

CREATE INDEX ix_company_invitation_company ON company_invitation (company_id, created_at);
-- At most one usable invitation per company at a time.
CREATE UNIQUE INDEX uq_company_invitation_pending ON company_invitation (company_id) WHERE status = 'PENDING';

GRANT SELECT, INSERT, UPDATE ON company_invitation TO transithub_runtime;

ALTER TABLE company_invitation ENABLE ROW LEVEL SECURITY;
CREATE POLICY company_invitation_platform ON company_invitation
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());

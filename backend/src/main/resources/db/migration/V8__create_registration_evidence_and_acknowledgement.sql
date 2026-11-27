-- Evidence and representative acknowledgements (Release 1 spec 5.1–5.2;
-- AC-R1-CAP-06, AC-R1-ACK-01..04). Platform data, like the registration.
--
-- File contents are not stored here: only metadata and the key of the
-- encrypted file in file storage (R-17).

CREATE TABLE registration_evidence (
    id               UUID PRIMARY KEY,
    registration_id  UUID          NOT NULL REFERENCES company_registration (id),
    evidence_type    VARCHAR(40)   NOT NULL,
    file_name        VARCHAR(200)  NOT NULL,
    content_type     VARCHAR(100)  NOT NULL,
    size_bytes       BIGINT        NOT NULL,
    sha256           CHAR(64)      NOT NULL,
    storage_key      VARCHAR(200)  NOT NULL,
    uploaded_by      VARCHAR(100)  NOT NULL,
    uploaded_at      TIMESTAMPTZ   NOT NULL,
    removed_at       TIMESTAMPTZ,
    removed_by       VARCHAR(100),

    CONSTRAINT chk_registration_evidence_size CHECK (size_bytes > 0),
    CONSTRAINT chk_registration_evidence_removal CHECK ((removed_at IS NULL) = (removed_by IS NULL))
);

CREATE INDEX ix_registration_evidence_registration ON registration_evidence (registration_id);

CREATE TABLE registration_acknowledgement (
    id                     UUID PRIMARY KEY,
    registration_id        UUID          NOT NULL REFERENCES company_registration (id),
    representative_id      UUID          NOT NULL REFERENCES registration_representative (id),
    outcome                VARCHAR(20)   NOT NULL,     -- ACKNOWLEDGED or REFUSED
    refusal_reason         VARCHAR(2000),
    signature_storage_key  VARCHAR(200),
    signature_sha256       CHAR(64),
    declaration_version    VARCHAR(40),
    content_revision       INTEGER       NOT NULL,     -- the draft content that was acknowledged
    recorded_by            VARCHAR(100)  NOT NULL,     -- the officer operating the device
    recorded_at            TIMESTAMPTZ   NOT NULL,
    invalidated_at         TIMESTAMPTZ,
    invalidation_reason    VARCHAR(500),

    CONSTRAINT chk_registration_acknowledgement_outcome CHECK (outcome IN ('ACKNOWLEDGED', 'REFUSED')),
    CONSTRAINT chk_registration_acknowledgement_signed CHECK (
        (outcome = 'ACKNOWLEDGED' AND signature_storage_key IS NOT NULL AND declaration_version IS NOT NULL)
        OR (outcome = 'REFUSED' AND refusal_reason IS NOT NULL))
);

CREATE INDEX ix_registration_acknowledgement_registration ON registration_acknowledgement (registration_id);

GRANT SELECT, INSERT, UPDATE ON registration_evidence        TO transithub_runtime;
GRANT SELECT, INSERT, UPDATE ON registration_acknowledgement TO transithub_runtime;

ALTER TABLE registration_evidence        ENABLE ROW LEVEL SECURITY;
ALTER TABLE registration_acknowledgement ENABLE ROW LEVEL SECURITY;

CREATE POLICY registration_evidence_platform ON registration_evidence
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY registration_acknowledgement_platform ON registration_acknowledgement
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());

-- Company registrations (BP-01.01; Release 1 spec 5.1, AC-R1-CAP-01..05).
--
-- A registration exists before any Company. It is platform data: only
-- platform staff (Registration Officers and others) work with it, so its
-- row-level security admits the platform scope only. The permanent Company
-- record is created later, on approval (step 5).

CREATE SEQUENCE registration_reference_seq START 1;

CREATE TABLE company_registration (
    id                   UUID PRIMARY KEY,          -- may be generated offline by the device
    reference            VARCHAR(20)  NOT NULL,     -- REG-<year>-<6 digits>, assigned by the server
    status               VARCHAR(30)  NOT NULL,
    channel              VARCHAR(10)  NOT NULL,     -- FIELD (tablet) or ONLINE (browser)
    captured_by          VARCHAR(100) NOT NULL,     -- user id of the capturing officer
    content_revision     INTEGER      NOT NULL DEFAULT 1,  -- +1 on every material change

    -- company details
    legal_name           VARCHAR(200),
    trading_name         VARCHAR(200),
    registration_number  VARCHAR(100),
    business_type        VARCHAR(100),
    province             VARCHAR(100),
    district             VARCHAR(100),
    physical_address     VARCHAR(500),
    postal_address       VARCHAR(500),
    contact_email        VARCHAR(254),
    contact_phone        VARCHAR(30),

    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_company_registration_reference UNIQUE (reference),
    CONSTRAINT chk_company_registration_channel CHECK (channel IN ('FIELD', 'ONLINE')),
    CONSTRAINT chk_company_registration_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'UNDER_VERIFICATION', 'AWAITING_INFORMATION', 'RECOMMENDED',
        'APPROVED', 'REJECTED', 'WITHDRAWN'))
);

CREATE INDEX ix_company_registration_captured_by ON company_registration (captured_by, created_at);
CREATE INDEX ix_company_registration_status      ON company_registration (status, created_at);
-- For duplicate flagging (step 3c).
CREATE INDEX ix_company_registration_reg_number  ON company_registration (lower(registration_number));

CREATE TABLE registration_representative (
    id                        UUID PRIMARY KEY,
    registration_id           UUID         NOT NULL REFERENCES company_registration (id),
    position                  INTEGER      NOT NULL,           -- display order
    full_name                 VARCHAR(200) NOT NULL,
    id_document_type          VARCHAR(20),                     -- NRC, PASSPORT, OTHER
    id_document_number        VARCHAR(50),
    capacity                  VARCHAR(100),                    -- e.g. Director, Owner
    phone                     VARCHAR(30),
    email                     VARCHAR(254),
    acknowledgement_required  BOOLEAN      NOT NULL DEFAULT TRUE,
    primary_contact           BOOLEAN      NOT NULL DEFAULT FALSE,
    removed_at                TIMESTAMPTZ,                     -- removed from a draft; history kept
    created_at                TIMESTAMPTZ  NOT NULL,

    CONSTRAINT chk_registration_representative_id_type
        CHECK (id_document_type IS NULL OR id_document_type IN ('NRC', 'PASSPORT', 'OTHER'))
);

CREATE INDEX ix_registration_representative_registration ON registration_representative (registration_id);

-- Field-level change history of drafts (AC-R1-CAP-05): who changed what, when.
CREATE TABLE registration_change (
    id               UUID PRIMARY KEY,
    registration_id  UUID          NOT NULL REFERENCES company_registration (id),
    field            VARCHAR(100)  NOT NULL,
    old_value        VARCHAR(2000),
    new_value        VARCHAR(2000),
    material         BOOLEAN       NOT NULL,
    changed_by       VARCHAR(100)  NOT NULL,
    changed_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_registration_change_registration ON registration_change (registration_id, changed_at);

-- Status history (ADR-0006, AC-R1-X-03).
CREATE TABLE registration_status_history (
    id               UUID PRIMARY KEY,
    registration_id  UUID          NOT NULL REFERENCES company_registration (id),
    from_status      VARCHAR(30),
    to_status        VARCHAR(30)   NOT NULL,
    actor_id         VARCHAR(100)  NOT NULL,
    reason           VARCHAR(2000),
    occurred_at      TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_registration_status_history_registration ON registration_status_history (registration_id, occurred_at);

-- Grants: no DELETE anywhere. History tables are append-only.
GRANT SELECT, INSERT, UPDATE ON company_registration        TO transithub_runtime;
GRANT SELECT, INSERT, UPDATE ON registration_representative TO transithub_runtime;
GRANT SELECT, INSERT         ON registration_change         TO transithub_runtime;
GRANT SELECT, INSERT         ON registration_status_history TO transithub_runtime;
GRANT USAGE ON SEQUENCE registration_reference_seq TO transithub_runtime;

-- Row-level security: platform scope only.
ALTER TABLE company_registration        ENABLE ROW LEVEL SECURITY;
ALTER TABLE registration_representative ENABLE ROW LEVEL SECURITY;
ALTER TABLE registration_change         ENABLE ROW LEVEL SECURITY;
ALTER TABLE registration_status_history ENABLE ROW LEVEL SECURITY;

CREATE POLICY company_registration_platform ON company_registration
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY registration_representative_platform ON registration_representative
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY registration_change_platform ON registration_change
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY registration_status_history_platform ON registration_status_history
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());

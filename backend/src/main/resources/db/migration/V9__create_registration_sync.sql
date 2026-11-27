-- Offline synchronisation of registrations (Release 1 spec 5.3,
-- AC-R1-SYN-01..08; BR-ONB-023–029). Platform data.

-- Who changed the server copy last (for separation of duties on conflicts),
-- and whether an unresolved sync conflict blocks submission.
ALTER TABLE company_registration ADD COLUMN last_changed_by  VARCHAR(100);
ALTER TABLE company_registration ADD COLUMN open_conflict_id UUID;
UPDATE company_registration SET last_changed_by = captured_by WHERE last_changed_by IS NULL;
ALTER TABLE company_registration ALTER COLUMN last_changed_by SET NOT NULL;

-- Every sync attempt per registration (AC-R1-SYN-04).
CREATE TABLE registration_sync_record (
    id                    UUID PRIMARY KEY,
    registration_id       UUID          NOT NULL,     -- the id the device sent (may be quarantined, so no FK)
    device_id             VARCHAR(100)  NOT NULL,
    user_id               VARCHAR(100)  NOT NULL,
    payload_hash          CHAR(64)      NOT NULL,     -- SHA-256 of the snapshot: detects retries (AC-R1-SYN-02)
    base_version          BIGINT,                     -- the server version the device last saw
    outcome               VARCHAR(20)   NOT NULL,
    server_version_after  BIGINT,
    conflict_id           UUID,
    quarantine_id         UUID,
    received_at           TIMESTAMPTZ   NOT NULL,

    CONSTRAINT chk_registration_sync_record_outcome
        CHECK (outcome IN ('CREATED', 'UPDATED', 'ALREADY_APPLIED', 'CONFLICT', 'QUARANTINED'))
);

CREATE INDEX ix_registration_sync_record_lookup
    ON registration_sync_record (registration_id, device_id, received_at);

-- Both versions are kept until an authorised person decides (AC-R1-SYN-05, -06).
CREATE TABLE registration_sync_conflict (
    id                  UUID PRIMARY KEY,
    registration_id     UUID          NOT NULL REFERENCES company_registration (id),
    device_id           VARCHAR(100)  NOT NULL,
    device_user         VARCHAR(100)  NOT NULL,
    server_editor       VARCHAR(100)  NOT NULL,
    base_version        BIGINT,
    server_version      BIGINT        NOT NULL,
    device_snapshot     JSONB         NOT NULL,
    server_snapshot     JSONB         NOT NULL,
    detected_at         TIMESTAMPTZ   NOT NULL,
    resolution          VARCHAR(20),               -- KEEP_SERVER, USE_DEVICE, USE_MERGED
    resolved_snapshot   JSONB,
    resolution_reason   VARCHAR(2000),
    resolved_by         VARCHAR(100),
    resolved_at         TIMESTAMPTZ,

    CONSTRAINT chk_registration_sync_conflict_resolution
        CHECK (resolution IS NULL OR resolution IN ('KEEP_SERVER', 'USE_DEVICE', 'USE_MERGED')),
    CONSTRAINT chk_registration_sync_conflict_resolved
        CHECK ((resolution IS NULL) = (resolved_at IS NULL))
);

CREATE INDEX ix_registration_sync_conflict_registration ON registration_sync_conflict (registration_id);

-- Data that arrived but cannot be applied: kept, never lost (AC-R1-SYN-07).
CREATE TABLE registration_sync_quarantine (
    id               UUID PRIMARY KEY,
    registration_id  UUID          NOT NULL,     -- the id the device sent
    device_id        VARCHAR(100)  NOT NULL,
    user_id          VARCHAR(100)  NOT NULL,
    reason           VARCHAR(500)  NOT NULL,
    payload          JSONB         NOT NULL,
    received_at      TIMESTAMPTZ   NOT NULL,
    released_at      TIMESTAMPTZ,
    released_by      VARCHAR(100),
    release_note     VARCHAR(2000)
);

CREATE INDEX ix_registration_sync_quarantine_open ON registration_sync_quarantine (received_at) WHERE released_at IS NULL;

GRANT SELECT, INSERT         ON registration_sync_record     TO transithub_runtime;
GRANT SELECT, INSERT, UPDATE ON registration_sync_conflict   TO transithub_runtime;
GRANT SELECT, INSERT, UPDATE ON registration_sync_quarantine TO transithub_runtime;

ALTER TABLE registration_sync_record     ENABLE ROW LEVEL SECURITY;
ALTER TABLE registration_sync_conflict   ENABLE ROW LEVEL SECURITY;
ALTER TABLE registration_sync_quarantine ENABLE ROW LEVEL SECURITY;

CREATE POLICY registration_sync_record_platform ON registration_sync_record
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY registration_sync_conflict_platform ON registration_sync_conflict
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY registration_sync_quarantine_platform ON registration_sync_quarantine
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());

-- Idempotency keys for create and money operations (ADR-0007, AC-R1-X-05).
-- A retried request with the same key replays the stored response instead of
-- running again.

CREATE TABLE idempotency_record (
    request_scope          VARCHAR(600)  NOT NULL,  -- HTTP method + path
    idempotency_key        VARCHAR(255)  NOT NULL,
    request_hash           CHAR(64)      NOT NULL,  -- SHA-256 of the body, hex
    state                  VARCHAR(20)   NOT NULL,
    response_status        INTEGER,
    response_content_type  VARCHAR(200),
    response_location      VARCHAR(2000),
    response_body          BYTEA,
    created_at             TIMESTAMPTZ   NOT NULL,
    completed_at           TIMESTAMPTZ,

    CONSTRAINT pk_idempotency_record PRIMARY KEY (request_scope, idempotency_key),
    CONSTRAINT chk_idempotency_record_state CHECK (state IN ('IN_PROGRESS', 'COMPLETED'))
);

-- For purging old records.
CREATE INDEX ix_idempotency_record_created_at ON idempotency_record (created_at);

-- Business audit trail (ADR-0008, AC-R1-X-02).
-- Rows are written in the same transaction as the change they describe and
-- can never be changed or removed: the triggers below reject UPDATE, DELETE
-- and TRUNCATE for every role, including the table owner.

CREATE TABLE audit_event (
    id              UUID PRIMARY KEY,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    actor_id        VARCHAR(100)  NOT NULL,
    action          VARCHAR(100)  NOT NULL,
    entity_type     VARCHAR(100)  NOT NULL,
    entity_id       UUID          NOT NULL,
    company_id      UUID,
    reason          VARCHAR(2000),
    details         JSONB,
    correlation_id  VARCHAR(100),

    CONSTRAINT chk_audit_event_action_not_blank CHECK (LENGTH(TRIM(action)) > 0)
);

CREATE INDEX ix_audit_event_entity  ON audit_event (entity_type, entity_id, occurred_at);
CREATE INDEX ix_audit_event_company ON audit_event (company_id, occurred_at);

CREATE FUNCTION reject_audit_event_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only (% rejected)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER trg_audit_event_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_audit_event_change();

CREATE TRIGGER trg_audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_event_change();

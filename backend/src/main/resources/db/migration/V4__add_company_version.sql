-- Optimistic locking (ADR-0007): a second conflicting write is refused.
ALTER TABLE companies ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

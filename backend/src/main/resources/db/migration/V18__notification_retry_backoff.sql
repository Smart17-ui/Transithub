-- Backlog #13 / review: a failed delivery waits longer before each retry (1, 2, 4 …
-- minutes, at most 6 hours apart), so a short email outage never exhausts the retries.
ALTER TABLE notification_outbox ADD COLUMN next_attempt_at TIMESTAMPTZ;

CREATE INDEX ix_notification_outbox_due ON notification_outbox (next_attempt_at, created_at) WHERE sent_at IS NULL;

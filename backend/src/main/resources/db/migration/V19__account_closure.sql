-- Closing unused applicant accounts (Release 1 §9: SS-14 "close my account",
-- SS-16 "deleted at 90 days if no application"; backlog #18 and #19).
--
-- The application has no DELETE rights (ADR-0003, ADR-0008), so an account is not
-- removed row by row. Closing it deletes the sign-in account at the identity
-- provider and erases the personal data here: name, email and the link to the
-- sign-in account. What stays is an anonymous row (id, dates, status CLOSED), so
-- audit events that point at it still resolve. Only accounts that never held a role
-- and never had an application can be closed, so no work record loses its author.

ALTER TABLE user_account DROP CONSTRAINT chk_user_account_status;
ALTER TABLE user_account ADD CONSTRAINT chk_user_account_status
    CHECK (status IN ('ACTIVE', 'DISABLED', 'CLOSED'));

ALTER TABLE user_account ADD COLUMN closed_at              TIMESTAMPTZ;
ALTER TABLE user_account ADD COLUMN closed_reason          VARCHAR(30);
ALTER TABLE user_account ADD COLUMN unused_warning_sent_at TIMESTAMPTZ;

ALTER TABLE user_account ADD CONSTRAINT chk_user_account_closed
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)
       AND (closed_at IS NULL OR closed_reason IN ('BY_USER', 'UNUSED')));

-- A closed account keeps no personal data.
ALTER TABLE user_account ADD CONSTRAINT chk_user_account_closed_is_anonymous
    CHECK (status <> 'CLOSED' OR (email IS NULL AND identity_subject = 'closed:' || id::text));

-- The daily clean-up looks for old active accounts.
CREATE INDEX ix_user_account_active_created ON user_account (created_at) WHERE status = 'ACTIVE';

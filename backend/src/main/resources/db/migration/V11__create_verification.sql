-- Verification (Release 1 spec 5.5; AC-R1-VER-01..07; BR-ONB-045–058)
-- and the notification outbox. Platform data.

ALTER TABLE company_registration  ADD COLUMN assigned_verifier VARCHAR(100);
-- Evidence supplied in answer to an information request (AC-R1-VER-05).
ALTER TABLE registration_evidence ADD COLUMN information_request_id UUID;
-- The verifier's review of each possible duplicate (AC-R1-SUB-02 follow-up).
ALTER TABLE registration_duplicate_flag ADD COLUMN review_note VARCHAR(2000);
ALTER TABLE registration_duplicate_flag ADD CONSTRAINT chk_registration_duplicate_flag_review
    CHECK (review_outcome IS NULL OR review_outcome IN ('NOT_DUPLICATE', 'DUPLICATE'));

-- G1 basic: who was asked to verify, by whom and why; reassignment keeps history (AC-R1-VER-01, -06).
CREATE TABLE verification_assignment (
    id               UUID PRIMARY KEY,
    registration_id  UUID          NOT NULL REFERENCES company_registration (id),
    assignee         VARCHAR(100)  NOT NULL,
    assigned_by      VARCHAR(100)  NOT NULL,
    reason           VARCHAR(2000),
    assigned_at      TIMESTAMPTZ   NOT NULL,
    ended_at         TIMESTAMPTZ,
    end_reason       VARCHAR(500)
);
CREATE INDEX ix_verification_assignment_registration ON verification_assignment (registration_id, assigned_at);
-- At most one current verifier per registration.
CREATE UNIQUE INDEX uq_verification_assignment_current
    ON verification_assignment (registration_id) WHERE ended_at IS NULL;

-- One assessment per evidence item; a newer one supersedes (AC-R1-VER-03).
CREATE TABLE evidence_assessment (
    id               UUID PRIMARY KEY,
    registration_id  UUID          NOT NULL REFERENCES company_registration (id),
    evidence_id      UUID          NOT NULL REFERENCES registration_evidence (id),
    outcome          VARCHAR(20)   NOT NULL,
    findings         VARCHAR(4000),
    assessed_by      VARCHAR(100)  NOT NULL,
    assessed_at      TIMESTAMPTZ   NOT NULL,
    superseded_at    TIMESTAMPTZ,
    CONSTRAINT chk_evidence_assessment_outcome CHECK (outcome IN ('ACCEPTED', 'INSUFFICIENT', 'INVALID'))
);
CREATE INDEX ix_evidence_assessment_registration ON evidence_assessment (registration_id);

CREATE TABLE information_request (
    id               UUID PRIMARY KEY,
    registration_id  UUID          NOT NULL REFERENCES company_registration (id),
    description      VARCHAR(4000) NOT NULL,
    due_date         DATE          NOT NULL,
    requested_by     VARCHAR(100)  NOT NULL,
    requested_at     TIMESTAMPTZ   NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    response         VARCHAR(4000),
    responded_by     VARCHAR(100),
    responded_at     TIMESTAMPTZ,
    CONSTRAINT chk_information_request_status CHECK (status IN ('OPEN', 'ANSWERED')),
    CONSTRAINT chk_information_request_answered CHECK ((status = 'ANSWERED') = (responded_at IS NOT NULL))
);
CREATE INDEX ix_information_request_registration ON information_request (registration_id);

CREATE TABLE verification_recommendation (
    id               UUID PRIMARY KEY,
    registration_id  UUID          NOT NULL REFERENCES company_registration (id),
    recommendation   VARCHAR(10)   NOT NULL,
    reasons          VARCHAR(4000) NOT NULL,
    recommended_by   VARCHAR(100)  NOT NULL,
    recommended_at   TIMESTAMPTZ   NOT NULL,
    superseded_at    TIMESTAMPTZ,              -- when a reviewer challenges it (step 5b)
    CONSTRAINT chk_verification_recommendation CHECK (recommendation IN ('APPROVE', 'REJECT'))
);
CREATE INDEX ix_verification_recommendation_registration ON verification_recommendation (registration_id);

-- Notifications are written in the same transaction as the business change
-- and delivered later by a sender (R-13, backlog #13): nothing is lost if
-- SMS or email is down.
CREATE TABLE notification_outbox (
    id                 UUID PRIMARY KEY,
    company_id         UUID,                      -- NULL for platform notifications
    recipient_user_id  VARCHAR(100)  NOT NULL,
    template           VARCHAR(100)  NOT NULL,
    parameters         JSONB         NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    sent_at            TIMESTAMPTZ,
    attempts           INTEGER       NOT NULL DEFAULT 0,
    last_error         VARCHAR(2000)
);
CREATE INDEX ix_notification_outbox_unsent ON notification_outbox (created_at) WHERE sent_at IS NULL;

GRANT SELECT, INSERT, UPDATE ON verification_assignment     TO transithub_runtime;
GRANT SELECT, INSERT, UPDATE ON evidence_assessment         TO transithub_runtime;
GRANT SELECT, INSERT, UPDATE ON information_request         TO transithub_runtime;
GRANT SELECT, INSERT, UPDATE ON verification_recommendation TO transithub_runtime;
GRANT SELECT, INSERT, UPDATE ON notification_outbox         TO transithub_runtime;

ALTER TABLE verification_assignment     ENABLE ROW LEVEL SECURITY;
ALTER TABLE evidence_assessment         ENABLE ROW LEVEL SECURITY;
ALTER TABLE information_request         ENABLE ROW LEVEL SECURITY;
ALTER TABLE verification_recommendation ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_outbox         ENABLE ROW LEVEL SECURITY;

CREATE POLICY verification_assignment_platform ON verification_assignment
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY evidence_assessment_platform ON evidence_assessment
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY information_request_platform ON information_request
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY verification_recommendation_platform ON verification_recommendation
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY notification_outbox_scope ON notification_outbox
    USING (app_is_platform_scope() OR company_id = app_current_company_id())
    WITH CHECK (app_is_platform_scope() OR company_id = app_current_company_id());

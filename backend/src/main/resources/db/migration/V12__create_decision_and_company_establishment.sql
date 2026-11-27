-- Review, decision and company establishment (Release 1 spec 5.6–5.7;
-- AC-R1-DEC-01..06, AC-R1-EST-01..03; BR-ONB-038, 059–067, 080).

-- Registration side of the decision.
ALTER TABLE company_registration ADD COLUMN recommendation_endorsed_by VARCHAR(100);
ALTER TABLE company_registration ADD COLUMN appeal_deadline TIMESTAMPTZ;
ALTER TABLE company_registration ADD COLUMN company_id UUID;

-- Independent review of the verifier's recommendation (BR-ONB-062).
CREATE TABLE recommendation_review (
    id                 UUID PRIMARY KEY,
    registration_id    UUID          NOT NULL REFERENCES company_registration (id),
    recommendation_id  UUID          NOT NULL REFERENCES verification_recommendation (id),
    outcome            VARCHAR(20)   NOT NULL,
    comments           VARCHAR(4000),
    reviewed_by        VARCHAR(100)  NOT NULL,
    reviewed_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_recommendation_review_outcome CHECK (outcome IN ('ENDORSE', 'CHALLENGE'))
);
CREATE INDEX ix_recommendation_review_registration ON recommendation_review (registration_id);

-- The formal decision. One per registration in Release 1 (appeals: 1.1).
CREATE TABLE registration_decision (
    id               UUID PRIMARY KEY,
    registration_id  UUID          NOT NULL REFERENCES company_registration (id),
    outcome          VARCHAR(20)   NOT NULL,
    reasons          VARCHAR(4000) NOT NULL,
    decided_by       VARCHAR(100)  NOT NULL,
    decided_at       TIMESTAMPTZ   NOT NULL,
    appeal_deadline  TIMESTAMPTZ,
    company_id       UUID,
    CONSTRAINT chk_registration_decision_outcome CHECK (outcome IN ('APPROVED', 'REJECTED')),
    CONSTRAINT chk_registration_decision_shape CHECK (
        (outcome = 'APPROVED' AND company_id IS NOT NULL AND appeal_deadline IS NULL)
        OR (outcome = 'REJECTED' AND company_id IS NULL AND appeal_deadline IS NOT NULL)),
    CONSTRAINT uq_registration_decision UNIQUE (registration_id)
);

-- Company side: generated code, link to its registration, operational status.
CREATE SEQUENCE company_code_seq START 1;

ALTER TABLE companies ADD COLUMN registration_id UUID REFERENCES company_registration (id);
-- AC-R1-EST-02: a registration establishes at most one company, whatever happens.
ALTER TABLE companies ADD CONSTRAINT uq_companies_registration UNIQUE (registration_id);
-- Approval is not operation (BR-ONB-038): a new company waits for activation.
ALTER TABLE companies ADD COLUMN operational_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE companies ADD CONSTRAINT chk_companies_operational_status
    CHECK (operational_status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'));

GRANT SELECT, INSERT ON recommendation_review TO transithub_runtime;
GRANT SELECT, INSERT ON registration_decision TO transithub_runtime;
GRANT USAGE ON SEQUENCE company_code_seq TO transithub_runtime;

ALTER TABLE recommendation_review ENABLE ROW LEVEL SECURITY;
ALTER TABLE registration_decision ENABLE ROW LEVEL SECURITY;

CREATE POLICY recommendation_review_platform ON recommendation_review
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY registration_decision_platform ON registration_decision
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());

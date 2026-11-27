-- Submission and duplicate flagging (Release 1 spec 5.4; AC-R1-SUB-01..03;
-- BR-ONB-020, 028, 043–044). Platform data.

-- What exactly was submitted, kept forever. The runtime role may only
-- insert and read: a submitted version can never be changed (AC-R1-SUB-01).
CREATE TABLE registration_submission (
    id                UUID PRIMARY KEY,
    registration_id   UUID          NOT NULL REFERENCES company_registration (id),
    submission_no     INTEGER       NOT NULL,       -- 1 for the first submission
    content_revision  INTEGER       NOT NULL,
    snapshot          JSONB         NOT NULL,
    submitted_by      VARCHAR(100)  NOT NULL,
    submitted_at      TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uq_registration_submission_no UNIQUE (registration_id, submission_no)
);

-- Possible duplicates, for the verifier to review. Never merged or discarded
-- automatically (AC-R1-SUB-02).
CREATE TABLE registration_duplicate_flag (
    id                 UUID PRIMARY KEY,
    registration_id    UUID          NOT NULL REFERENCES company_registration (id),
    match_kind         VARCHAR(20)   NOT NULL,      -- REGISTRATION or COMPANY
    matched_id         UUID          NOT NULL,
    matched_reference  VARCHAR(40)   NOT NULL,      -- REG-... or the company code
    match_reason       VARCHAR(40)   NOT NULL,      -- REGISTRATION_NUMBER, NAME_AND_DISTRICT, LEGAL_NAME
    flagged_at         TIMESTAMPTZ   NOT NULL,
    reviewed_at        TIMESTAMPTZ,
    reviewed_by        VARCHAR(100),
    review_outcome     VARCHAR(20),                 -- set by the verifier in step 5

    CONSTRAINT chk_registration_duplicate_flag_kind CHECK (match_kind IN ('REGISTRATION', 'COMPANY')),
    CONSTRAINT chk_registration_duplicate_flag_reason
        CHECK (match_reason IN ('REGISTRATION_NUMBER', 'NAME_AND_DISTRICT', 'LEGAL_NAME'))
);

CREATE INDEX ix_registration_duplicate_flag_registration ON registration_duplicate_flag (registration_id);
CREATE INDEX ix_company_registration_name_district
    ON company_registration (lower(legal_name), lower(district));
CREATE INDEX ix_companies_registration_number ON companies (lower(registration_number));
CREATE INDEX ix_companies_legal_name ON companies (lower(legal_name));

GRANT SELECT, INSERT         ON registration_submission     TO transithub_runtime;
GRANT SELECT, INSERT, UPDATE ON registration_duplicate_flag TO transithub_runtime;

ALTER TABLE registration_submission     ENABLE ROW LEVEL SECURITY;
ALTER TABLE registration_duplicate_flag ENABLE ROW LEVEL SECURITY;

CREATE POLICY registration_submission_platform ON registration_submission
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());
CREATE POLICY registration_duplicate_flag_platform ON registration_duplicate_flag
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());

-- Lighter, traceable verification (decision log, 27 Nov 2026).
--
-- 1. Companies give their operating licence number; it is checked against the
--    licensing authority and kept on the established company for tracing.
-- 2. The verifier records each check against an official register (PACRA,
--    RTSA, ZRA): which number, what the register showed, and a note.
-- 3. Recommendation, review and separate approval are retired: one checker
--    decides. recommendation_review and verification_recommendation stay for
--    history only; company_registration.recommendation_endorsed_by is no
--    longer written.

ALTER TABLE company_registration ADD COLUMN operating_licence_number VARCHAR(100);
ALTER TABLE companies            ADD COLUMN operating_licence_number VARCHAR(100);

CREATE TABLE registry_check (
    id               UUID          PRIMARY KEY,
    registration_id  UUID          NOT NULL REFERENCES company_registration (id),
    source           VARCHAR(20)   NOT NULL,
    reference        VARCHAR(100)  NOT NULL,
    result           VARCHAR(20)   NOT NULL,
    note             VARCHAR(2000),
    checked_by       VARCHAR(100)  NOT NULL,
    checked_at       TIMESTAMPTZ   NOT NULL,

    CONSTRAINT chk_registry_check_source CHECK (source IN ('PACRA', 'RTSA', 'ZRA', 'OTHER')),
    CONSTRAINT chk_registry_check_result CHECK (result IN ('MATCH', 'MISMATCH', 'NOT_FOUND', 'COULD_NOT_CHECK'))
);

CREATE INDEX ix_registry_check_registration ON registry_check (registration_id, source, checked_at);

-- Insert-only: a later check supersedes an earlier one by time, never by editing it.
GRANT SELECT, INSERT ON registry_check TO transithub_runtime;

ALTER TABLE registry_check ENABLE ROW LEVEL SECURITY;
CREATE POLICY registry_check_platform ON registry_check
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());

-- Self-service company applications (Release 1 §9, SS-1, SS-7, SS-9..SS-11).
--
-- A person who created their own account applies for their bus company online.
-- Such an application belongs to them (applicant_user_id); in the new "applicant"
-- scope the database shows and changes ONLY their own application and its parts,
-- whatever the application code asks for (ADR-0003: app checks plus row-level security).

-- 1. Channel and owner --------------------------------------------------------
ALTER TABLE company_registration ALTER COLUMN channel TYPE VARCHAR(20);
ALTER TABLE company_registration DROP CONSTRAINT chk_company_registration_channel;
ALTER TABLE company_registration ADD CONSTRAINT chk_company_registration_channel
    CHECK (channel IN ('FIELD', 'ONLINE', 'SELF_SERVICE'));

ALTER TABLE company_registration ADD COLUMN applicant_user_id UUID REFERENCES user_account (id);

-- A self-service application always has an applicant; staff-captured ones never do.
ALTER TABLE company_registration ADD CONSTRAINT chk_company_registration_applicant
    CHECK ((channel = 'SELF_SERVICE') = (applicant_user_id IS NOT NULL));

CREATE INDEX ix_company_registration_applicant
    ON company_registration (applicant_user_id, created_at)
    WHERE applicant_user_id IS NOT NULL;

-- SS-11: one open application per applicant. A new one may start only after the
-- previous one was approved, declined or withdrawn (SS-12).
CREATE UNIQUE INDEX uq_company_registration_open_per_applicant
    ON company_registration (applicant_user_id)
    WHERE applicant_user_id IS NOT NULL AND status NOT IN ('APPROVED', 'REJECTED', 'WITHDRAWN');

-- 2. Applicant scope helpers ---------------------------------------------------
-- app.scope = 'applicant' and app.user_id = the signed-in user's TransitHub id,
-- set on every connection checkout like app.scope and app.company_id.
CREATE FUNCTION app_current_applicant_id() RETURNS uuid
LANGUAGE sql STABLE AS $$
    SELECT CASE
        WHEN coalesce(current_setting('app.scope', true), '') = 'applicant'
        THEN nullif(current_setting('app.user_id', true), '')::uuid
    END
$$;

-- Owner's rights, so child-table policies can look at the parent row; it only
-- ever answers "is this registration the current applicant's own?".
CREATE FUNCTION app_owns_registration(p_registration_id UUID) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1 FROM company_registration r
         WHERE r.id = p_registration_id
           AND r.applicant_user_id IS NOT NULL
           AND r.applicant_user_id = app_current_applicant_id())
$$;

REVOKE ALL ON FUNCTION app_owns_registration(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_current_applicant_id() TO transithub_runtime;
GRANT EXECUTE ON FUNCTION app_owns_registration(UUID) TO transithub_runtime;

-- 3. Row-level security for applicants (added to the existing platform policies) -
CREATE POLICY company_registration_applicant ON company_registration
    USING (applicant_user_id = app_current_applicant_id())
    WITH CHECK (applicant_user_id = app_current_applicant_id());

CREATE POLICY registration_representative_applicant ON registration_representative
    USING (app_owns_registration(registration_id)) WITH CHECK (app_owns_registration(registration_id));
CREATE POLICY registration_change_applicant ON registration_change
    USING (app_owns_registration(registration_id)) WITH CHECK (app_owns_registration(registration_id));
CREATE POLICY registration_status_history_applicant ON registration_status_history
    USING (app_owns_registration(registration_id)) WITH CHECK (app_owns_registration(registration_id));
CREATE POLICY registration_evidence_applicant ON registration_evidence
    USING (app_owns_registration(registration_id)) WITH CHECK (app_owns_registration(registration_id));
CREATE POLICY registration_acknowledgement_applicant ON registration_acknowledgement
    USING (app_owns_registration(registration_id)) WITH CHECK (app_owns_registration(registration_id));
CREATE POLICY registration_submission_applicant ON registration_submission
    USING (app_owns_registration(registration_id)) WITH CHECK (app_owns_registration(registration_id));

-- Applicants record audit events about their own application only; they cannot read the trail.
CREATE POLICY audit_event_applicant_insert ON audit_event
    FOR INSERT
    WITH CHECK (company_id IS NULL
                AND entity_type = 'CompanyRegistration'
                AND app_owns_registration(entity_id));

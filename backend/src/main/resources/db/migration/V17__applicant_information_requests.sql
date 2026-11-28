-- SS-8: the applicant answers the Compliance Officer's information requests in their
-- own account (Release 1 §9). In the applicant scope they may read and answer the
-- requests on their own application only, and tell its assigned Compliance Officer
-- that the information was provided.

CREATE POLICY information_request_applicant ON information_request
    USING (app_owns_registration(registration_id))
    WITH CHECK (app_owns_registration(registration_id));

-- Only one kind of notification, only to the Compliance Officer assigned to the
-- applicant's own application.
CREATE POLICY notification_outbox_applicant_insert ON notification_outbox
    FOR INSERT
    WITH CHECK (company_id IS NULL
                AND template = 'registration.information-provided'
                AND EXISTS (SELECT 1 FROM company_registration r
                             WHERE r.applicant_user_id = app_current_applicant_id()
                               AND r.assigned_verifier = notification_outbox.recipient_user_id));

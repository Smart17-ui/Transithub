-- Enquiries sent through the public Contact form (website).
-- Personal data: kept only to reply (consent recorded), readable only by
-- platform staff holding enquiry.view; never visible to a company scope.

CREATE SEQUENCE contact_enquiry_reference_seq;

CREATE TABLE contact_enquiry (
    id            UUID          PRIMARY KEY,
    reference     VARCHAR(20)   NOT NULL,
    topic         VARCHAR(30)   NOT NULL,
    full_name     VARCHAR(200)  NOT NULL,
    email         VARCHAR(254)  NOT NULL,
    phone         VARCHAR(30),
    company_name  VARCHAR(200),
    message       VARCHAR(4000) NOT NULL,
    consent_given BOOLEAN       NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    received_at   TIMESTAMPTZ   NOT NULL,
    resolved_at   TIMESTAMPTZ,
    resolved_by   VARCHAR(100),

    CONSTRAINT uq_contact_enquiry_reference UNIQUE (reference),
    CONSTRAINT chk_contact_enquiry_topic CHECK (topic IN ('COMPANY_REGISTRATION', 'TRAVEL', 'PARTNERSHIP', 'OTHER')),
    CONSTRAINT chk_contact_enquiry_status CHECK (status IN ('NEW', 'RESOLVED')),
    CONSTRAINT chk_contact_enquiry_consent CHECK (consent_given),
    CONSTRAINT chk_contact_enquiry_resolution CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

CREATE INDEX ix_contact_enquiry_received ON contact_enquiry (status, received_at DESC);

GRANT SELECT, INSERT, UPDATE ON contact_enquiry TO transithub_runtime;
GRANT USAGE ON SEQUENCE contact_enquiry_reference_seq TO transithub_runtime;

ALTER TABLE contact_enquiry ENABLE ROW LEVEL SECURITY;
CREATE POLICY contact_enquiry_platform ON contact_enquiry
    USING (app_is_platform_scope()) WITH CHECK (app_is_platform_scope());

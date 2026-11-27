CREATE TABLE companies (
    id UUID PRIMARY KEY,

    company_code VARCHAR(20) NOT NULL,
    legal_name VARCHAR(200) NOT NULL,
    trading_name VARCHAR(200),

    registration_number VARCHAR(100),
    contact_email VARCHAR(254),
    contact_phone VARCHAR(30),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_companies_company_code UNIQUE (company_code),
    CONSTRAINT chk_companies_company_code_not_blank
        CHECK (LENGTH(TRIM(company_code)) > 0),
    CONSTRAINT chk_companies_legal_name_not_blank
        CHECK (LENGTH(TRIM(legal_name)) > 0)
);

-- Employer accounts are independent of job-seeker member accounts.
CREATE TABLE employer_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(80) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    manager_name VARCHAR(80) NOT NULL,
    manager_phone VARCHAR(20) NULL,
    company_name VARCHAR(150) NOT NULL,
    business_registration_number VARCHAR(12) NOT NULL,
    representative_name VARCHAR(80) NOT NULL,
    opening_date VARCHAR(8) NOT NULL,
    company_address VARCHAR(255) NULL,
    nts_verified BOOLEAN NOT NULL DEFAULT FALSE,
    nts_checked_at DATETIME NULL,
    nts_raw_response TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(255) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_employer_accounts_login_id (login_id),
    UNIQUE KEY uq_employer_accounts_email (email),
    UNIQUE KEY uq_employer_accounts_business_reg (business_registration_number),
    CONSTRAINT fk_employer_accounts_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES members (id)
);

ALTER TABLE job_postings
    ADD COLUMN employer_account_id BIGINT NULL AFTER source_provider,
    ADD CONSTRAINT fk_job_postings_employer_account FOREIGN KEY (employer_account_id) REFERENCES employer_accounts (id),
    ADD KEY ix_job_postings_employer_account (employer_account_id);

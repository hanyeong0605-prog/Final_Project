ALTER TABLE employer_accounts
    ADD COLUMN passwordless_status VARCHAR(24) NOT NULL DEFAULT 'NONE' AFTER reviewed_at,
    ADD COLUMN passwordless_user_id VARCHAR(100) NULL AFTER passwordless_status,
    ADD COLUMN passwordless_activated_at DATETIME NULL AFTER passwordless_user_id,
    ADD COLUMN passwordless_last_verified_at DATETIME NULL AFTER passwordless_activated_at,
    ADD UNIQUE KEY uq_employer_passwordless_user_id (passwordless_user_id);

UPDATE employer_accounts
SET passwordless_status = 'ENROLL_REQUIRED',
    passwordless_user_id = CONCAT('EMPLOYER:', id)
WHERE status = 'APPROVED';

ALTER TABLE opportunities
    ADD COLUMN training_ncs_code VARCHAR(100) NULL,
    ADD COLUMN training_contents TEXT NULL,
    ADD COLUMN training_certificate VARCHAR(1000) NULL,
    ADD COLUMN training_grade VARCHAR(100) NULL,
    ADD COLUMN employment_rate_3m VARCHAR(50) NULL,
    ADD COLUMN employment_rate_6m VARCHAR(50) NULL,
    ADD COLUMN thumbnail_url VARCHAR(1500) NULL;

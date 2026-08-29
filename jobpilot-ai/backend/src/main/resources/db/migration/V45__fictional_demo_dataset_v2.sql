-- Versioned portfolio-only dataset. Real crawled postings and employer postings are never selected
-- by the replacement seeder; only FICTIONAL_DEMO / SYNTHETIC_DEMO rows are eligible.
CREATE TABLE portfolio_demo_dataset_versions (
    dataset_name VARCHAR(80) NOT NULL PRIMARY KEY,
    dataset_version INT NOT NULL,
    installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(500) NOT NULL
);

ALTER TABLE company_reviews
    ADD COLUMN department VARCHAR(150) NULL AFTER display_author,
    ADD COLUMN employment_status VARCHAR(30) NULL AFTER department,
    ADD COLUMN tenure_months INT NULL AFTER employment_status,
    ADD COLUMN management_message VARCHAR(2000) NULL AFTER body,
    ADD CONSTRAINT ck_review_employment_status
        CHECK (employment_status IS NULL OR employment_status IN ('CURRENT','FORMER')),
    ADD CONSTRAINT ck_review_tenure
        CHECK (tenure_months IS NULL OR tenure_months BETWEEN 1 AND 600);

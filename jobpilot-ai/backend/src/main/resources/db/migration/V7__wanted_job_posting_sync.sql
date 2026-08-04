ALTER TABLE job_postings
    ADD COLUMN source_provider VARCHAR(30) NOT NULL DEFAULT 'WANTED' AFTER external_job_id,
    ADD COLUMN source_company_id VARCHAR(150) NULL AFTER source_provider,
    ADD COLUMN company_logo_url VARCHAR(1500) NULL AFTER company_url,
    ADD COLUMN is_entry_level BOOLEAN NULL AFTER experience_type;

ALTER TABLE job_postings
    DROP INDEX uk_job_postings_external_job_id,
    ADD UNIQUE KEY uk_job_postings_source_external_job_id (source_provider, external_job_id),
    ADD KEY ix_job_postings_source_company_id (source_provider, source_company_id);

CREATE TABLE job_posting_locations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_posting_id BIGINT NOT NULL,
    source_provider VARCHAR(30) NOT NULL,
    source_location_id VARCHAR(100) NOT NULL,
    location_text VARCHAR(255) NULL,
    sido VARCHAR(50) NULL,
    sigungu VARCHAR(100) NULL,
    detailed_address VARCHAR(500) NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_posting_locations_source (job_posting_id, source_provider, source_location_id),
    KEY ix_job_posting_locations_lat_lng (latitude, longitude),
    CONSTRAINT fk_job_posting_locations_job FOREIGN KEY (job_posting_id) REFERENCES job_postings (id)
);

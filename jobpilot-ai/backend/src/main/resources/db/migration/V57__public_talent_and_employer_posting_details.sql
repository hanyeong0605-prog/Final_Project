ALTER TABLE member_profiles ADD COLUMN talent_public BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE job_postings
    ADD COLUMN employer_qualifications TEXT NULL,
    ADD COLUMN employer_preferred_qualifications TEXT NULL,
    ADD COLUMN employer_image_url VARCHAR(1500) NULL;

CREATE TABLE employer_talent_favorites (
    employer_account_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (employer_account_id, member_id),
    CONSTRAINT fk_employer_talent_favorites_employer FOREIGN KEY (employer_account_id) REFERENCES employer_accounts(id),
    CONSTRAINT fk_employer_talent_favorites_member FOREIGN KEY (member_id) REFERENCES members(id)
);

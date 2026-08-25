CREATE TABLE employer_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employer_account_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    job_posting_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(500) NOT NULL,
    url VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_employer_notifications_owner (employer_account_id, is_read, created_at),
    CONSTRAINT fk_employer_notifications_owner FOREIGN KEY (employer_account_id) REFERENCES employer_accounts (id),
    CONSTRAINT fk_employer_notifications_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_employer_notifications_job FOREIGN KEY (job_posting_id) REFERENCES job_postings (id)
);

CREATE TABLE member_job_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    job_posting_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_member_job_events_member_created (member_id, created_at),
    KEY ix_member_job_events_job_created (job_posting_id, created_at),
    CONSTRAINT fk_member_job_events_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_member_job_events_job FOREIGN KEY (job_posting_id) REFERENCES job_postings (id)
);

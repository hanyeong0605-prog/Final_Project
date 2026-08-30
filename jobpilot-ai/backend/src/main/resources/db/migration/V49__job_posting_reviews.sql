CREATE TABLE job_posting_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_posting_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    rating INT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    employment_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_review_posting FOREIGN KEY (job_posting_id) REFERENCES job_postings(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_review_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
    CONSTRAINT uq_job_review_member UNIQUE (job_posting_id, member_id),
    CONSTRAINT chk_job_review_rating CHECK (rating BETWEEN 1 AND 5)
);

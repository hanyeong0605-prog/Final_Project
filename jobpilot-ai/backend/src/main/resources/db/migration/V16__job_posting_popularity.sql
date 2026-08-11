ALTER TABLE job_postings
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0 AFTER crawled_at,
    ADD KEY ix_job_postings_popularity (status, view_count);

CREATE INDEX ix_user_interests_job_popularity
    ON user_interests (target_type, target_id);

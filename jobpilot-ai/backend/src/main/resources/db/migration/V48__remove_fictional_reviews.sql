-- Retire the portfolio-only company review experiment without touching community sentiment.
-- Historical migrations V43/V45 remain immutable; this migration removes their runtime schema
-- and all FICTIONAL_DEMO postings plus dependent records created for that experiment.
CREATE TEMPORARY TABLE retired_fictional_postings (
    id BIGINT NOT NULL PRIMARY KEY
);

INSERT INTO retired_fictional_postings (id)
SELECT id
FROM job_postings
WHERE source_provider = 'FICTIONAL_DEMO';

DROP TABLE IF EXISTS company_review_moderation_events;
DROP TABLE IF EXISTS company_review_reports;
DROP TABLE IF EXISTS company_review_likes;
DROP TABLE IF EXISTS company_review_analyses;
DROP TABLE IF EXISTS company_reviews;
DROP TABLE IF EXISTS review_company_postings;
DROP TABLE IF EXISTS review_companies;
DROP TABLE IF EXISTS portfolio_demo_dataset_versions;

DELETE FROM notification_logs
WHERE target_type = 'JOB_POSTING'
  AND target_id IN (SELECT id FROM retired_fictional_postings);

DELETE FROM user_interests
WHERE target_type = 'JOB_POSTING'
  AND target_id IN (SELECT id FROM retired_fictional_postings);

DELETE FROM planner_events
WHERE source_type = 'JOB_POSTING'
  AND source_id IN (SELECT id FROM retired_fictional_postings);

DELETE FROM member_job_events
WHERE job_posting_id IN (SELECT id FROM retired_fictional_postings);

DELETE FROM employer_notifications
WHERE job_posting_id IN (SELECT id FROM retired_fictional_postings);

DELETE evidence
FROM job_match_evidences evidence
JOIN job_matches job_match ON job_match.id = evidence.job_match_id
JOIN retired_fictional_postings retired ON retired.id = job_match.job_posting_id;

DELETE evidence
FROM job_match_evidences evidence
JOIN job_requirements requirement ON requirement.id = evidence.job_requirement_id
JOIN retired_fictional_postings retired ON retired.id = requirement.job_posting_id;

DELETE FROM job_matches
WHERE job_posting_id IN (SELECT id FROM retired_fictional_postings);

DELETE FROM job_skills
WHERE job_posting_id IN (SELECT id FROM retired_fictional_postings);

DELETE FROM job_requirement_extraction_status
WHERE job_posting_id IN (SELECT id FROM retired_fictional_postings);

DELETE FROM job_requirements
WHERE job_posting_id IN (SELECT id FROM retired_fictional_postings);

DELETE FROM job_posting_locations
WHERE job_posting_id IN (SELECT id FROM retired_fictional_postings);

DELETE FROM job_postings
WHERE id IN (SELECT id FROM retired_fictional_postings);

DROP TEMPORARY TABLE retired_fictional_postings;

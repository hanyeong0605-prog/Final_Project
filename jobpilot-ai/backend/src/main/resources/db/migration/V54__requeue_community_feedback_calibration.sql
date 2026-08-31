-- Re-evaluate public posts after adding explicit service-feedback calibration.
-- Private Q&A remains excluded by the worker's claim predicate.
UPDATE community_posts
SET analysis_state = 'PENDING', analysis_attempts = 0, next_analysis_at = CURRENT_TIMESTAMP
WHERE status = 'PUBLIC' AND private_post = FALSE;

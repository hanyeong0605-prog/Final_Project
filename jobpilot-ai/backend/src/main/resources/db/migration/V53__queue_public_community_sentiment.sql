-- Analyze every public post for the administrator's community mood overview.
-- Private Q&A remains excluded from automated analysis.
UPDATE community_posts
SET analysis_state = 'PENDING', analysis_attempts = 0, next_analysis_at = CURRENT_TIMESTAMP
WHERE status = 'PUBLIC' AND private_post = FALSE AND analysis_state = 'SKIPPED';

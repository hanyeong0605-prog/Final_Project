INSERT INTO planner_events (
    member_id, source_type, source_id, event_type, title,
    starts_at, ends_at, all_day, created_at
)
SELECT
    ui.member_id,
    'JOB_POSTING',
    jp.id,
    'APPLICATION_PERIOD',
    CONCAT(CASE WHEN jp.company_name IS NULL OR jp.company_name = '' THEN '' ELSE CONCAT(jp.company_name, ' · ') END, jp.title),
    COALESCE(jp.published_at, jp.deadline_at),
    jp.deadline_at,
    TRUE,
    CURRENT_TIMESTAMP
FROM user_interests ui
JOIN job_postings jp ON jp.id = ui.target_id
LEFT JOIN planner_events pe
    ON pe.member_id = ui.member_id
    AND pe.source_type = 'JOB_POSTING'
    AND pe.source_id = jp.id
    AND pe.event_type = 'APPLICATION_PERIOD'
WHERE ui.target_type = 'JOB_POSTING'
  AND COALESCE(jp.published_at, jp.deadline_at) IS NOT NULL
  AND pe.id IS NULL;

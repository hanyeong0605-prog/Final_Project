-- A certificate can have several written/practical exam rounds in one year.
-- Keep one event per phase and start date instead of allowing only one phase per bookmark.
ALTER TABLE planner_events
    DROP INDEX uk_planner_events_member_source_type,
    ADD UNIQUE KEY uk_planner_events_member_source_schedule
        (member_id, source_type, source_id, event_type, starts_at);

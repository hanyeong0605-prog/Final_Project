-- Persist the fields used by the in-app notification history.
ALTER TABLE notification_logs
    ADD COLUMN title VARCHAR(200) NULL AFTER notification_type,
    ADD COLUMN body VARCHAR(500) NULL AFTER title,
    ADD COLUMN url VARCHAR(300) NULL AFTER body,
    ADD COLUMN is_read TINYINT(1) NOT NULL DEFAULT 0 AFTER url;

CREATE INDEX ix_notification_logs_member_sent ON notification_logs (member_id, sent_at DESC);

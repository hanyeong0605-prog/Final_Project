-- 알림 목록에서 삭제해도 notification_logs의 중복 발송 방지 이력은 유지한다.
-- 숨긴 알림만 헤더 드롭다운/읽지 않은 수에서 제외한다.
ALTER TABLE notification_logs
    ADD COLUMN is_hidden TINYINT(1) NOT NULL DEFAULT 0 AFTER is_read;

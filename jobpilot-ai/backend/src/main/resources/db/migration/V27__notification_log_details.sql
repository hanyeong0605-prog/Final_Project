-- 2026-08-13: 상단바 종 아이콘 알림 드롭다운을 위해 notification_logs를 "발송 여부만 기록하는
-- 중복방지 테이블"에서 "실제로 화면에 보여줄 수 있는 알림 이력"으로 확장한다.
-- title/body/url은 WebPushService.sendToMember()에 넘기는 값과 동일한 걸 그대로 같이
-- 저장해서, 공고가 나중에 삭제/변경되거나 target_id로 다시 조인하지 않아도 알림 목록을
-- 그대로 재현할 수 있게 한다(발송 당시 스냅샷). is_read는 드롭다운에서 읽음 처리용.
ALTER TABLE notification_logs
    ADD COLUMN title VARCHAR(200) NULL AFTER notification_type,
    ADD COLUMN body VARCHAR(500) NULL AFTER title,
    ADD COLUMN url VARCHAR(300) NULL AFTER body,
    ADD COLUMN is_read TINYINT(1) NOT NULL DEFAULT 0 AFTER url;

CREATE INDEX ix_notification_logs_member_sent ON notification_logs (member_id, sent_at DESC);

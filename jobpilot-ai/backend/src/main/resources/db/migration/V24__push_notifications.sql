-- 웹푸시(Web Push) 알림 인프라.
-- push_subscriptions: 브라우저가 Push API로 구독하면 돌려주는 endpoint/키 3종을 저장한다.
--   같은 회원이 여러 기기(폰/PC)에서 구독할 수 있어 member_id 1:N, endpoint는 브라우저별로
--   유일하므로 UNIQUE로 잡아서 재구독 시 갱신(INSERT ... ON DUPLICATE KEY 대신 애플리케이션
--   레이어에서 upsert)한다.
CREATE TABLE push_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    endpoint VARCHAR(1000) NOT NULL,
    p256dh VARCHAR(255) NOT NULL,
    auth_key VARCHAR(255) NOT NULL,
    user_agent VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint(500)),
    CONSTRAINT fk_push_subscriptions_member FOREIGN KEY (member_id) REFERENCES members (id)
);

CREATE INDEX ix_push_subscriptions_member ON push_subscriptions (member_id);

-- notification_logs: "이 회원에게 이 대상(공고 등)으로 이 종류의 알림을 이미 보냈는지"를
-- 기록해서 스케줄러가 매일 돌 때 중복 발송하지 않게 막는 용도. UserInterest/JobPosting을
-- FK로 강하게 묶지 않은 이유는 앞으로 공고 외 다른 target_type(추천 공고, 자격증 등)도
-- 같은 테이블을 재사용할 수 있게 범용으로 열어둔 것 - user_interests, certificate_bookmarks가
-- 이미 각자 다른 도메인이라 여기서 특정 FK로 고정하면 재사용성이 떨어진다.
CREATE TABLE notification_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    sent_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_notification_logs UNIQUE (member_id, target_type, target_id, notification_type),
    CONSTRAINT fk_notification_logs_member FOREIGN KEY (member_id) REFERENCES members (id)
);

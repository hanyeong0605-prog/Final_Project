-- 2026-08-10: 결제 모델을 1회성 크레딧 구매에서 월 구독(자동결제/빌링)으로 바꾼다("크레딧
-- 말고 구독으로 바꿔줘"). V14가 이미 로컬에 적용된 상태라(마이페이지에서 "0 크레딧" 화면
-- 확인됨) V14 파일을 직접 고치면 Flyway 체크섬이 깨진다 - 그래서 V15에서 credit_* 테이블을
-- 지우고 구독용 테이블을 새로 만든다. FK 때문에 credit_transactions(payments 참조) ->
-- payments -> credit_balances 순서로 지운다.
DROP TABLE credit_transactions;
DROP TABLE payments;
DROP TABLE credit_balances;

-- 회원 1명당 1행 - 지금 구독 상태(활성/해지)와 다음 결제 예정일을 담는다. billing_key는
-- 토스 자동결제 가이드의 "빌링키"(카드 등록 후 재인증 없이 결제할 수 있게 해주는 토큰) -
-- 카드 정보 자체는 저장하지 않고 이 키만 저장한다.
CREATE TABLE subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    customer_key VARCHAR(64) NOT NULL,
    billing_key VARCHAR(200) NULL,
    plan_id VARCHAR(30) NOT NULL,
    price_won INT NOT NULL,
    status VARCHAR(20) NOT NULL, -- ACTIVE | CANCELED
    current_period_start DATETIME NULL,
    current_period_end DATETIME NULL,
    next_billing_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    canceled_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_subscriptions_member (member_id),
    UNIQUE KEY uq_subscriptions_customer_key (customer_key),
    CONSTRAINT fk_subscriptions_member FOREIGN KEY (member_id) REFERENCES members (id)
);

-- 매달 실행되는 자동결제 시도 이력(성공/실패 모두) - append-only.
CREATE TABLE subscription_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(20) NOT NULL, -- PAID | FAILED
    failure_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_subscription_payments_order_id (order_id),
    KEY ix_subscription_payments_subscription (subscription_id, created_at),
    CONSTRAINT fk_subscription_payments_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT fk_subscription_payments_member FOREIGN KEY (member_id) REFERENCES members (id)
);

-- 2026-08-10: 결제(크레딧) 기능(태스크 #40) - docs/interview-credit-schema.md 기준.
-- 문서는 원래 credit_transactions.related_interview_attempt_id로 "언제 어떤 질문 생성에
-- 크레딧을 썼는지"까지 남기려 했는데, 그 기반이 되는 interview_attempts 테이블은 이번
-- 범위에서 만들지 않았다(타임라인 기능에서 InterviewSessionRecord를 세션 단위로 이미
-- 만들어놨고, 질문 단위 소비 추적은 별도 설계가 더 필요함). 그래서 이번엔 "충전(구매)"
-- 경로만 완성하고, related_payment_id로 결제-거래내역만 연결한다. 크레딧 "소비"를 특정
-- 기능(모의면접 질문 생성 등)에 자동으로 연결하는 배선은 다음 범위.
CREATE TABLE credit_balances (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    balance INT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_credit_balances_member (member_id),
    CONSTRAINT fk_credit_balances_member FOREIGN KEY (member_id) REFERENCES members (id)
);

-- PG(토스페이먼츠) 결제 건. order_id는 결제창을 띄우기 전에 우리 쪽에서 먼저 발급해서
-- PENDING으로 만들어두고(TossPaymentsClient 호출 전), 결제창 승인 흐름이 끝나면
-- pg_payment_key를 채우고 상태를 PAID/FAILED로 바꾼다 - 토스 "결제 승인 API" 가이드의
-- orderId/paymentKey 흐름 그대로. credit_transactions가 이 테이블을 참조하므로 먼저 만든다.
CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    pg_provider VARCHAR(20) NOT NULL DEFAULT 'toss',
    order_id VARCHAR(64) NOT NULL,
    pg_payment_key VARCHAR(200) NULL,
    amount INT NOT NULL, -- 결제 금액(원)
    credit_amount INT NOT NULL, -- 지급될 크레딧 수
    status VARCHAR(20) NOT NULL, -- PENDING | PAID | FAILED | CANCELED
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at DATETIME NULL,
    canceled_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_payments_order_id (order_id),
    KEY ix_payments_member (member_id, requested_at),
    CONSTRAINT fk_payments_member FOREIGN KEY (member_id) REFERENCES members (id)
);

-- 원장(ledger) 테이블 - 절대 UPDATE/DELETE하지 않는다(InterviewSessionRecord와 같은
-- "과거 기록" 원칙). balance_after는 그 거래 직후의 잔액 스냅샷 - 감사/디버깅용.
CREATE TABLE credit_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL, -- PURCHASE | CONSUME | REFUND | GRANT
    amount INT NOT NULL, -- 충전/지급은 양수, 소비는 음수
    balance_after INT NOT NULL,
    related_payment_id BIGINT NULL,
    memo VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_credit_transactions_member (member_id, created_at),
    CONSTRAINT fk_credit_transactions_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_credit_transactions_payment FOREIGN KEY (related_payment_id) REFERENCES payments (id)
);

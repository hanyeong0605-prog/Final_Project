-- 2026-08-29: 구독(기간 무제한) → 이용권(횟수) 전환.
--
-- 자동결제(토스 빌링)를 못 쓰는 구조라 "구독"이 실제로는 1개월 단발 결제였고, 사용자가
-- 얻는 건 실전면접 기능 하나뿐이라 월정액의 명분이 약했다. 산 만큼 쓰는 횟수제로 바꾼다.
--
-- 새 테이블을 만들지 않고 기존 subscriptions 행(회원당 1행)을 그대로 재사용한다 -
-- 결제/주문 이력(subscription_payments)도 그대로 쓴다.
ALTER TABLE subscriptions
    -- 남은 실전면접 횟수. 결제 승인 시 상품 수량만큼 더하고, 세션 질문 조립이 성공하면 1 뺀다.
    ADD COLUMN remaining_sessions INT NOT NULL DEFAULT 0,
    -- 무료 월 1회를 언제 지급했는지(yyyy-MM). 매달 첫 실전면접 시도에서 이 값이 이번 달이
    -- 아니면 1회를 지급하고 이 값을 갱신한다 - 별도 카운터 테이블 없이 "월 1회"를 만든다.
    ADD COLUMN free_granted_month VARCHAR(7) NULL;

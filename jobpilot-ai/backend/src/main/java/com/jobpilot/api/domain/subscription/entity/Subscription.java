package com.jobpilot.api.domain.subscription.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// 2026-08-10: 구독(정기결제) 기능 - "크레딧 말고 구독으로" 요청에 따라 크레딧 원장 방식
// 대신 회원 1명당 구독 상태 1행을 둔다(무제한 이용권 모델이라 잔액 개념이 없다).
//
// billingKey 컬럼은 원래 토스 자동결제(빌링)용으로 만들었는데, 그 API가 테스트 환경에서도
// 별도 계약이 필요해서("테스트 결제만 되면 된다" 피드백) 일반 결제창 방식으로 바꿨다 -
// 그래서 이 필드는 항상 null이다(스키마만 남아있음, TossPaymentsClient/SubscriptionService
// 참고). 즉 "진짜 자동결제"가 아니라 달마다 사용자가 결제창에서 다시 결제해야 연장된다.
//
// activate()/cancel()이 유일한 상태 변경 경로이고 항상 SubscriptionService가
// SubscriptionPayment 저장과 같은 트랜잭션 안에서 호출한다.
@Entity
@Table(name = "subscriptions")
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "customer_key", nullable = false, unique = true, length = 64)
    private String customerKey;

    @Column(name = "billing_key", length = 200)
    private String billingKey;

    @Column(name = "plan_id", nullable = false, length = 30)
    private String planId;

    @Column(name = "price_won", nullable = false)
    private int priceWon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "next_billing_at")
    private LocalDateTime nextBillingAt;

    // 2026-08-29: 이용권(횟수) 전환 - 아래 두 필드가 실제 이용 자격을 결정한다.
    // status/currentPeriod*/nextBillingAt은 기간 구독 시절의 흔적이라 더는 자격 판정에
    // 쓰지 않는다(결제 이력과의 호환을 위해 컬럼만 남겨둔다).
    @Column(name = "remaining_sessions", nullable = false)
    private int remainingSessions;

    /** 무료 월 1회를 지급한 달(yyyy-MM). 별도 카운터 테이블 없이 "월 1회"를 만드는 장치. */
    @Column(name = "free_granted_month", length = 7)
    private String freeGrantedMonth;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    protected Subscription() {}

    // planId/priceWon도 생성자에서 받는다 - plan_id 컬럼이 NOT NULL이라 activate() 전
    // (미가입 상태로 처음 저장될 때)에도 값이 있어야 한다. activate()가 불리면 어차피
    // 덮어써진다.
    public Subscription(Long memberId, String customerKey, String planId, int priceWon) {
        this.memberId = memberId;
        this.customerKey = customerKey;
        this.planId = planId;
        this.priceWon = priceWon;
        this.status = SubscriptionStatus.CANCELED; // activate()가 불릴 때까지는 미가입 취급
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 결제 성공 직후 호출 - 산 만큼 횟수를 더한다(기존 잔여분은 유지된다).
     *
     * 2026-08-29: 예전에는 "지금부터 1개월"로 기간을 잡았는데, 이용권에는 유효기간을 두지
     * 않기로 했다 - 산 걸 계속 쓸 수 있는 쪽이 단순하고, 만료 자동해지 스케줄러
     * (SubscriptionService.expireOverdueSubscriptions)가 잔여 횟수가 남은 이용권을
     * 해지해버리는 사고도 막는다. nextBillingAt을 null로 두면 그 스케줄러의 조회 조건
     * (findByStatusAndNextBillingAtLessThanEqual)에 애초에 걸리지 않는다.
     */
    public void addSessions(String planId, int priceWon, int sessions) {
        this.planId = planId;
        this.priceWon = priceWon;
        this.status = SubscriptionStatus.ACTIVE;
        this.remainingSessions += sessions;
        this.currentPeriodStart = LocalDateTime.now();
        this.currentPeriodEnd = null;
        this.nextBillingAt = null;
        this.canceledAt = null;
    }

    /**
     * 이번 달 무료 1회를 아직 안 받았으면 지급한다. 지급했으면 true.
     *
     * 무료 사용자도 실전면접이 뭔지 한 번은 써봐야 이용권을 살 마음이 생긴다. 매달 1회씩
     * 주면 다시 돌아올 이유도 생긴다.
     */
    public boolean grantMonthlyFreeIfEligible(String month) {
        if (month.equals(this.freeGrantedMonth)) return false;
        this.freeGrantedMonth = month;
        this.remainingSessions += 1;
        return true;
    }

    /** 실전면접 한 세션을 차감한다. 남은 횟수가 없으면 false(호출부가 구매를 안내한다). */
    public boolean consumeSession() {
        if (this.remainingSessions <= 0) return false;
        this.remainingSessions -= 1;
        return true;
    }

    /** 사용자가 직접 해지 - 이용권에는 정기결제가 없어서 실질적으로는 잔여 횟수를 버리는 것이다. */
    public void cancel() {
        this.status = SubscriptionStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.nextBillingAt = null;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getCustomerKey() { return customerKey; }
    public String getBillingKey() { return billingKey; }
    public String getPlanId() { return planId; }
    public int getPriceWon() { return priceWon; }
    public SubscriptionStatus getStatus() { return status; }
    public LocalDateTime getCurrentPeriodStart() { return currentPeriodStart; }
    public LocalDateTime getCurrentPeriodEnd() { return currentPeriodEnd; }
    public LocalDateTime getNextBillingAt() { return nextBillingAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCanceledAt() { return canceledAt; }
    public int getRemainingSessions() { return remainingSessions; }
    public String getFreeGrantedMonth() { return freeGrantedMonth; }
}

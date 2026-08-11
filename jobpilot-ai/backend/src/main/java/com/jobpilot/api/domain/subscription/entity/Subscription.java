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

    /** 결제 성공 직후 호출 - 신규 구독/재구독/수동 연장 모두 이 경로를 탄다(항상 지금부터 1개월). */
    public void activate(String planId, int priceWon) {
        this.planId = planId;
        this.priceWon = priceWon;
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodStart = LocalDateTime.now();
        this.currentPeriodEnd = this.currentPeriodStart.plusMonths(1);
        this.nextBillingAt = this.currentPeriodEnd;
        this.canceledAt = null;
    }

    /** 사용자가 직접 해지, 또는 결제 기간이 지나도록 재결제가 없으면 스케줄러가 호출 - 즉시 해지. */
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
}

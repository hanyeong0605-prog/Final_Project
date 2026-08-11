package com.jobpilot.api.domain.subscription.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// 2026-08-10: 구독 기능 - 결제창을 띄우기 전에 PENDING으로 먼저 만들고(orderId 발급),
// 결제창에서 돌아온 뒤 승인 API 결과에 따라 markPaid()/markFailed()로 확정한다 - Payment
// 엔티티(구 credit 도메인)와 같은 패턴. PENDING이 아닌 상태에서는 재처리를 막는다(중복 승인
// 방지).
@Entity
@Table(name = "subscription_payments")
public class SubscriptionPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionPaymentStatus status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SubscriptionPayment() {}

    public SubscriptionPayment(Long subscriptionId, Long memberId, String orderId, int amount) {
        this.subscriptionId = subscriptionId;
        this.memberId = memberId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = SubscriptionPaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markPaid() {
        if (this.status != SubscriptionPaymentStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 결제입니다: " + this.status);
        }
        this.status = SubscriptionPaymentStatus.PAID;
    }

    public void markFailed(String reason) {
        if (this.status != SubscriptionPaymentStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 결제입니다: " + this.status);
        }
        this.status = SubscriptionPaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public Long getId() { return id; }
    public Long getSubscriptionId() { return subscriptionId; }
    public Long getMemberId() { return memberId; }
    public String getOrderId() { return orderId; }
    public int getAmount() { return amount; }
    public SubscriptionPaymentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

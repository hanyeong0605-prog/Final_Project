package com.jobpilot.api.domain.subscription.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobpilot.api.domain.subscription.service.SubscriptionPlan;
import org.junit.jupiter.api.Test;

// 2026-08-29: 구독(기간) → 이용권(횟수) 전환의 핵심 규칙을 엔티티 수준에서 고정한다.
// 결제/차감 흐름 전체는 서비스가 조립하지만, "몇 회가 남는가"는 전부 여기서 결정된다.
class InterviewPassTest {
    private Subscription newPass() {
        SubscriptionPlan plan = SubscriptionPlan.current();
        return new Subscription(1L, "member-1", plan.id(), plan.priceWon());
    }

    @Test
    void startsWithNoSessions() {
        assertThat(newPass().getRemainingSessions()).isZero();
        assertThat(newPass().consumeSession()).isFalse();
    }

    @Test
    void purchaseAddsSessionsOnTopOfWhatIsLeft() {
        // 이용권은 유효기간이 없어서 남은 횟수를 버리지 않고 더한다.
        Subscription pass = newPass();
        pass.addSessions("five", 5_900, 5);
        pass.consumeSession();
        pass.addSessions("single", 1_500, 1);

        assertThat(pass.getRemainingSessions()).isEqualTo(5);
    }

    @Test
    void purchaseDoesNotSetABillingDeadline() {
        // nextBillingAt이 남아 있으면 만료 스케줄러가 잔여 횟수가 있는 이용권을 해지해버린다
        // (SubscriptionService.expireOverdueSubscriptions의 조회 조건).
        Subscription pass = newPass();
        pass.addSessions("ten", 9_900, 10);

        assertThat(pass.getNextBillingAt()).isNull();
        assertThat(pass.getCurrentPeriodEnd()).isNull();
        assertThat(pass.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void consumeStopsAtZero() {
        Subscription pass = newPass();
        pass.addSessions("single", 1_500, 1);

        assertThat(pass.consumeSession()).isTrue();
        assertThat(pass.consumeSession()).isFalse();
        assertThat(pass.getRemainingSessions()).isZero();
    }

    @Test
    void monthlyFreeIsGrantedOnlyOncePerMonth() {
        Subscription pass = newPass();

        assertThat(pass.grantMonthlyFreeIfEligible("2026-08")).isTrue();
        assertThat(pass.grantMonthlyFreeIfEligible("2026-08")).isFalse();
        assertThat(pass.getRemainingSessions()).isEqualTo(1);

        assertThat(pass.grantMonthlyFreeIfEligible("2026-09")).isTrue();
        assertThat(pass.getRemainingSessions()).isEqualTo(2);
    }

    @Test
    void monthlyFreeAddsToPurchasedSessions() {
        // 무료 1회가 구매분을 덮어쓰면 안 된다 - 산 사람이 손해를 보게 된다.
        Subscription pass = newPass();
        pass.addSessions("five", 5_900, 5);

        pass.grantMonthlyFreeIfEligible("2026-08");

        assertThat(pass.getRemainingSessions()).isEqualTo(6);
    }

    @Test
    void planPricesStayDistinctSoPaymentsCanBeMappedBack() {
        // 결제 승인 때 상품을 금액으로 되찾는다(subscription_payments에 상품 id가 없다) -
        // 가격이 겹치는 순간 엉뚱한 횟수가 충전된다.
        assertThat(SubscriptionPlan.all().stream().map(SubscriptionPlan::priceWon).distinct().count())
                .isEqualTo(SubscriptionPlan.all().size());
        assertThat(SubscriptionPlan.findByPrice(1_500).sessions()).isEqualTo(1);
        assertThat(SubscriptionPlan.findByPrice(5_900).sessions()).isEqualTo(5);
        assertThat(SubscriptionPlan.findByPrice(9_900).sessions()).isEqualTo(10);
    }

    @Test
    void unknownPlanOrPriceFallsBackInsteadOfBreakingCheckout() {
        // 구버전 프론트가 상품을 안 보내거나 모르는 id를 보내도 결제가 깨지지 않아야 한다.
        assertThat(SubscriptionPlan.findById(null)).isEqualTo(SubscriptionPlan.current());
        assertThat(SubscriptionPlan.findById("legacy-standard")).isEqualTo(SubscriptionPlan.current());
        assertThat(SubscriptionPlan.findByPrice(12_345)).isEqualTo(SubscriptionPlan.current());
    }
}

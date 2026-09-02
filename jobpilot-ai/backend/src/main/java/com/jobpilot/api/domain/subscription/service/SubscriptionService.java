package com.jobpilot.api.domain.subscription.service;

import com.jobpilot.api.domain.subscription.client.TossPaymentsClient;
import com.jobpilot.api.domain.subscription.dto.SubscriptionCheckoutResponse;
import com.jobpilot.api.domain.subscription.dto.SubscriptionConfirmRequest;
import com.jobpilot.api.domain.subscription.dto.SubscriptionPlanResponse;
import com.jobpilot.api.domain.subscription.dto.SubscriptionStatusResponse;
import com.jobpilot.api.domain.subscription.entity.Subscription;
import com.jobpilot.api.domain.subscription.entity.SubscriptionPayment;
import com.jobpilot.api.domain.subscription.entity.SubscriptionPaymentStatus;
import com.jobpilot.api.domain.subscription.entity.SubscriptionStatus;
import com.jobpilot.api.domain.subscription.exception.SubscriptionException;
import com.jobpilot.api.domain.subscription.repository.SubscriptionPaymentRepository;
import com.jobpilot.api.domain.subscription.repository.SubscriptionRepository;
import com.jobpilot.api.domain.admin.AdminAccessService;
import com.jobpilot.api.domain.notification.entity.NotificationLog;
import com.jobpilot.api.domain.notification.repository.NotificationLogRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// 2026-08-29: 이용권(횟수) 모델. 회원 1명당 subscriptions 행 하나에 남은 횟수를 들고 있고,
// 결제하면 상품 수량만큼 더하고(addSessions) 실전면접 질문 조립이 성공하면 1 뺀다
// (consumeSession). 무료 사용자에게도 매달 1회를 지급해서 실전면접이 뭔지 써보게 한다.
//
// 이용권에는 유효기간이 없다 - 산 걸 계속 쓸 수 있는 쪽이 단순하고, 자동결제를 못 쓰는
// 이 구조에서 "기간"은 사용자에게 손해로만 느껴진다. 그래서 만료 알림도 두지 않는다
// (횟수가 0이 되는 건 사용자가 앱 안에서 바로 보게 되므로 굳이 밖에서 알릴 필요가 없다).
//
// 아래는 기간 구독이던 시절의 배경 메모다. status/currentPeriod*/nextBillingAt 컬럼과
// expireOverdueSubscriptions가 그 흔적으로 남아 있다.
//
// 2026-08-10: 구독 기능 - "크레딧 말고 구독으로" 요청에 따라 잔액 소비 방식 대신 "구독
// 중이면 무제한 이용" 방식으로 만든다(단일 요금제, SubscriptionPlan.current()).
//
// 원래 토스 자동결제(빌링)로 카드를 등록해두고 매달 조용히 재청구하려 했는데, 그 API는
// 테스트 환경에서도 별도 계약이 필요해서 막혔다("테스트로 하는 거라 테스트 결제만 되면
// 된다"는 피드백) - 그래서 일반 결제창(계약 없이 문서 테스트 키로 바로 됨) 방식으로
// 바꿨다. 결과적으로 "진짜 자동결제"가 아니라 "달마다 사용자가 결제창에서 다시 결제해야
// 연장되는" 구조다 - checkout()/confirmPayment()가 매달 반복 호출되는 경로.
//
// 스케줄러는 그래서 "자동 재청구"가 아니라 "기간이 끝났는데 재결제가 없으면 자동 해지"만
// 한다 - 실제 서비스로 키우려면 만료 전 알림(이메일 등)을 추가하는 게 자연스럽다.
@Service
@Transactional
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final AdminAccessService adminAccess;
    private final NotificationLogRepository notifications;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionPaymentRepository paymentRepository,
            TossPaymentsClient tossPaymentsClient,
            AdminAccessService adminAccess,
            NotificationLogRepository notifications
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.tossPaymentsClient = tossPaymentsClient;
        this.adminAccess = adminAccess;
        this.notifications = notifications;
    }

    /** 판매 중인 이용권 상품 목록(1회/5회/10회) - 프론트가 이 중 하나를 골라 checkout한다. */
    public List<SubscriptionPlanResponse> getPlans() {
        return SubscriptionPlan.all().stream()
                .map(plan -> new SubscriptionPlanResponse(plan.id(), plan.displayName(), plan.priceWon(), plan.sessions()))
                .toList();
    }

    /**
     * 이번 달 무료 1회를 아직 안 받았으면 지급한다.
     *
     * 조회 시점에 지급하는 게 핵심이다 - 차감 시점에만 주면 화면이 "0회 남음"으로 보여서
     * 사용자가 시작 버튼을 누르지도 못하고, 그러면 지급 기회 자체가 오지 않는다.
     * 같은 달에는 몇 번을 호출해도 한 번만 지급된다(freeGrantedMonth 비교).
     */
    private void grantMonthlyFree(Subscription subscription) {
        subscription.grantMonthlyFreeIfEligible(YearMonth.now().toString());
    }

    // 2026-08-13: 관리자는 원래 결제 없이 항상 무제한 이용(그래서 checkout/cancel을 막아뒀었다)
    // 인데, 관리자 계정으로도 구독 켜기/끄기 버튼 동작 자체를 테스트해보고 싶다는 요청으로
    // 막았던 걸 풀었다 - 테스트 키(TOSS_SECRET_KEY=test_sk_...)라 실제 결제는 안 일어난다.
    // 대신 "진짜 구독"이 있으면 그걸 우선 보여주고, 없을 때만 기존처럼 무제한 표시로
    // 폴백한다 - 관리자가 테스트 결제를 실제로 완료하면 일반 회원처럼 해지 버튼도 뜬다.
    @Transactional
    public SubscriptionStatusResponse getStatus(Long memberId) {
        // 관리자는 결제 없이 항상 이용 가능 - 이용권 행을 만들지 않는다.
        if (adminAccess.isAdmin(memberId) && subscriptionRepository.findByMemberId(memberId).isEmpty()) {
            return new SubscriptionStatusResponse(true, "ADMIN", "관리자 계정", 0, null, null, true, 0);
        }
        Subscription subscription = getOrCreateSubscription(memberId);
        grantMonthlyFree(subscription);
        return toResponse(subscription);
    }

    /** 결제창을 띄우기 전에 호출 - PENDING 결제 건을 만들고 orderId/금액을 돌려준다. */
    public SubscriptionCheckoutResponse checkout(Long memberId, String planId) {
        Subscription subscription = getOrCreateSubscription(memberId);
        SubscriptionPlan plan = SubscriptionPlan.findById(planId);
        String orderId = "sub-" + UUID.randomUUID();
        paymentRepository.save(new SubscriptionPayment(subscription.getId(), memberId, orderId, plan.priceWon()));
        return new SubscriptionCheckoutResponse(orderId, plan.priceWon(), plan.displayName());
    }

    /**
     * 실전면접 한 세션을 차감한다.
     *
     * 질문 조립이 성공한 직후에 호출한다 - 시작 버튼 시점에 차감하면 Gemini가 전부 실패해서
     * 질문을 못 만들었는데도 횟수가 날아가고, 세션 종료 시점에 차감하면 질문 생성 비용은 이미
     * 다 나갔는데 중간에 나간 사용자가 공짜가 된다.
     */
    @Transactional
    public SubscriptionStatusResponse consumeSession(Long memberId) {
        if (adminAccess.isAdmin(memberId) && subscriptionRepository.findByMemberId(memberId).isEmpty()) {
            return new SubscriptionStatusResponse(true, "ADMIN", "관리자 계정", 0, null, null, true, 0);
        }
        Subscription subscription = getOrCreateSubscription(memberId);
        grantMonthlyFree(subscription);
        if (!subscription.consumeSession()) {
            throw new SubscriptionException("남은 실전면접 횟수가 없습니다. 이용권을 구매해 주세요.");
        }
        return toResponse(subscription);
    }

    /** 결제창 successUrl 리다이렉트 이후 호출 - 승인 API를 부르고 성공하면 구독을 (재)활성화한다. */
    public SubscriptionStatusResponse confirmPayment(Long memberId, SubscriptionConfirmRequest request) {
        SubscriptionPayment payment = paymentRepository.findByOrderIdAndMemberId(request.orderId(), memberId)
                .orElseThrow(() -> new ResourceNotFoundException("결제 건을 찾을 수 없습니다."));

        if (payment.getStatus() != SubscriptionPaymentStatus.PENDING) {
            throw new SubscriptionException("이미 처리된 결제입니다: " + payment.getStatus());
        }
        // 클라이언트가 금액을 조작해서 보낼 수 없게, checkout 시 서버가 정한 금액과
        // successUrl에서 돌아온 금액이 같은지 다시 확인한다(토스 가이드 권장사항).
        if (payment.getAmount() != request.amount()) {
            payment.markFailed("결제 금액 불일치");
            throw new SubscriptionException("결제 금액이 일치하지 않습니다.");
        }

        tossPaymentsClient.confirmPayment(request.paymentKey(), request.orderId(), request.amount());
        payment.markPaid();

        Subscription subscription = subscriptionRepository.findById(payment.getSubscriptionId())
                .orElseThrow(() -> new SubscriptionException("구독 정보를 찾을 수 없습니다."));
        // 어떤 상품을 샀는지는 결제 금액으로 되찾는다 - subscription_payments에 상품 id 컬럼이
        // 없고, 이 금액은 바로 위에서 "서버가 정한 금액과 같은지" 검증을 통과한 값이라 안전하다.
        SubscriptionPlan plan = SubscriptionPlan.findByPrice(payment.getAmount());
        subscription.addSessions(plan.id(), plan.priceWon(), plan.sessions());
        notifications.save(new NotificationLog(memberId, "SUBSCRIPTION_PAYMENT", payment.getSubscriptionId(), "SUBSCRIPTION_PAID",
                "이용권 결제가 완료되었습니다", plan.displayName() + " 결제가 완료되어 실전면접 " + plan.sessions() + "회가 충전되었습니다. 결제 금액: " + String.format("%,d원", payment.getAmount()), "/account"));

        return toResponse(subscription);
    }

    public SubscriptionStatusResponse cancel(Long memberId) {
        Subscription subscription = subscriptionRepository.findByMemberId(memberId)
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionException("구독 중이 아닙니다."));
        subscription.cancel();
        return toResponse(subscription);
    }

    /**
     * 매일 새벽 3시(KST) - 기간 구독 시절에 남은 행을 정리한다.
     *
     * 2026-08-29: 이용권으로 바꾸면서 addSessions()가 nextBillingAt을 null로 두기 때문에,
     * 새로 결제된 이용권은 여기 조회 조건에 걸리지 않는다. 전환 이전에 만들어진 기간 구독
     * 행만 정리 대상이고, 그마저도 남은 횟수가 있으면 건드리지 않는다 - 이용권은 유효기간이
     * 없다는 게 이번 전환의 핵심이라 잔여 횟수를 기간 때문에 날려서는 안 된다.
     */
    @Scheduled(cron = "${subscription.expiry.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void expireOverdueSubscriptions() {
        List<Subscription> due = subscriptionRepository.findByStatusAndNextBillingAtLessThanEqual(
                SubscriptionStatus.ACTIVE, LocalDateTime.now());
        for (Subscription subscription : due) {
            if (subscription.getRemainingSessions() > 0) continue;
            log.info("기간 구독 만료로 자동 해지 - memberId={}", subscription.getMemberId());
            subscription.cancel();
        }
    }

    private Subscription getOrCreateSubscription(Long memberId) {
        return subscriptionRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    SubscriptionPlan plan = SubscriptionPlan.current();
                    return subscriptionRepository.save(
                            new Subscription(memberId, "member-" + memberId, plan.id(), plan.priceWon()));
                });
    }

    private SubscriptionStatusResponse toResponse(Subscription s) {
        // 2026-08-29: 이용 가능 여부는 이제 status가 아니라 남은 횟수가 정한다.
        boolean usable = s.getRemainingSessions() > 0;
        return new SubscriptionStatusResponse(
                usable,
                s.getPlanId(),
                usable ? SubscriptionPlan.findById(s.getPlanId()).displayName() : null,
                s.getPriceWon(), s.getCurrentPeriodEnd(), s.getNextBillingAt(), false,
                s.getRemainingSessions());
    }
}

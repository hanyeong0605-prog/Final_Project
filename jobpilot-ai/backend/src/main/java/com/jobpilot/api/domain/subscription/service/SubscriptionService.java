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
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionPaymentRepository paymentRepository,
            TossPaymentsClient tossPaymentsClient,
            AdminAccessService adminAccess
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.tossPaymentsClient = tossPaymentsClient;
        this.adminAccess = adminAccess;
    }

    public SubscriptionPlanResponse getPlan() {
        SubscriptionPlan plan = SubscriptionPlan.current();
        return new SubscriptionPlanResponse(plan.id(), plan.displayName(), plan.priceWon());
    }

    // 2026-08-13: 관리자는 원래 결제 없이 항상 무제한 이용(그래서 checkout/cancel을 막아뒀었다)
    // 인데, 관리자 계정으로도 구독 켜기/끄기 버튼 동작 자체를 테스트해보고 싶다는 요청으로
    // 막았던 걸 풀었다 - 테스트 키(TOSS_SECRET_KEY=test_sk_...)라 실제 결제는 안 일어난다.
    // 대신 "진짜 구독"이 있으면 그걸 우선 보여주고, 없을 때만 기존처럼 무제한 표시로
    // 폴백한다 - 관리자가 테스트 결제를 실제로 완료하면 일반 회원처럼 해지 버튼도 뜬다.
    public SubscriptionStatusResponse getStatus(Long memberId) {
        return subscriptionRepository.findByMemberId(memberId)
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .map(this::toResponse)
                .orElseGet(() -> adminAccess.isAdmin(memberId)
                        ? new SubscriptionStatusResponse(true, "ADMIN", "관리자 계정", 0, null, null, true)
                        : new SubscriptionStatusResponse(false, null, null, 0, null, null, false));
    }

    /** 결제창을 띄우기 전에 호출 - PENDING 결제 건을 만들고 orderId/금액을 돌려준다. */
    public SubscriptionCheckoutResponse checkout(Long memberId) {
        Subscription subscription = getOrCreateSubscription(memberId);
        SubscriptionPlan plan = SubscriptionPlan.current();
        String orderId = "sub-" + UUID.randomUUID();
        paymentRepository.save(new SubscriptionPayment(subscription.getId(), memberId, orderId, plan.priceWon()));
        return new SubscriptionCheckoutResponse(orderId, plan.priceWon(), plan.displayName());
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
        SubscriptionPlan plan = SubscriptionPlan.current();
        subscription.activate(plan.id(), plan.priceWon());

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
     * 매일 새벽 3시(KST) - 결제 기간이 끝났는데 재결제가 없는 활성 구독을 자동 해지한다.
     * "진짜 자동결제"가 아니라서(클래스 docstring 참고) 여기서 재청구를 시도하지 않는다 -
     * 그냥 만료 처리만 한다.
     */
    @Scheduled(cron = "${subscription.expiry.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void expireOverdueSubscriptions() {
        List<Subscription> due = subscriptionRepository.findByStatusAndNextBillingAtLessThanEqual(
                SubscriptionStatus.ACTIVE, LocalDateTime.now());
        for (Subscription subscription : due) {
            log.info("구독 기간 만료로 자동 해지 - memberId={}", subscription.getMemberId());
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
        return new SubscriptionStatusResponse(
                s.getStatus() == SubscriptionStatus.ACTIVE,
                s.getPlanId(),
                s.getStatus() == SubscriptionStatus.ACTIVE ? SubscriptionPlan.current().displayName() : null,
                s.getPriceWon(), s.getCurrentPeriodEnd(), s.getNextBillingAt(), false);
    }
}

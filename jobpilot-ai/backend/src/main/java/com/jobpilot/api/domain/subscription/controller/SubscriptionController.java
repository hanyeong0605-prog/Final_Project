package com.jobpilot.api.domain.subscription.controller;

import com.jobpilot.api.domain.subscription.dto.SubscriptionCheckoutRequest;
import com.jobpilot.api.domain.subscription.dto.SubscriptionCheckoutResponse;
import com.jobpilot.api.domain.subscription.dto.SubscriptionConfirmRequest;
import com.jobpilot.api.domain.subscription.dto.SubscriptionPlanResponse;
import com.jobpilot.api.domain.subscription.dto.SubscriptionStatusResponse;
import com.jobpilot.api.domain.subscription.service.SubscriptionService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// 2026-08-10: 구독 기능 - InterviewSessionRecordController와 같은 원칙,
// /api/v1/members/me/** 라 별도 인증 게이팅이 필요 없다.
@RestController
@RequestMapping("/api/v1/members/me/subscription")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    // 2026-08-29: 이용권 상품이 1회/5회/10회 세 개로 늘어서 목록으로 내려준다.
    @GetMapping("/plans")
    public List<SubscriptionPlanResponse> plans() {
        return subscriptionService.getPlans();
    }

    @GetMapping
    public SubscriptionStatusResponse status(Authentication auth) {
        return subscriptionService.getStatus(AuthenticatedMember.id(auth));
    }

    @PostMapping("/checkout")
    public SubscriptionCheckoutResponse checkout(Authentication auth, @RequestBody(required = false) SubscriptionCheckoutRequest request) {
        return subscriptionService.checkout(AuthenticatedMember.id(auth), request == null ? null : request.planId());
    }

    /** 실전면접 세션 하나를 차감한다 - 프론트가 질문 조립에 성공한 직후 호출한다. */
    @PostMapping("/consume")
    public SubscriptionStatusResponse consume(Authentication auth) {
        return subscriptionService.consumeSession(AuthenticatedMember.id(auth));
    }

    @PostMapping("/confirm")
    public SubscriptionStatusResponse confirm(Authentication auth, @Valid @RequestBody SubscriptionConfirmRequest request) {
        return subscriptionService.confirmPayment(AuthenticatedMember.id(auth), request);
    }

    @PostMapping("/cancel")
    public SubscriptionStatusResponse cancel(Authentication auth) {
        return subscriptionService.cancel(AuthenticatedMember.id(auth));
    }
}

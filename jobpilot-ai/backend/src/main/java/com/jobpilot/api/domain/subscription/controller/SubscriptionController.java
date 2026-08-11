package com.jobpilot.api.domain.subscription.controller;

import com.jobpilot.api.domain.subscription.dto.SubscriptionCheckoutResponse;
import com.jobpilot.api.domain.subscription.dto.SubscriptionConfirmRequest;
import com.jobpilot.api.domain.subscription.dto.SubscriptionPlanResponse;
import com.jobpilot.api.domain.subscription.dto.SubscriptionStatusResponse;
import com.jobpilot.api.domain.subscription.service.SubscriptionService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
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

    @GetMapping("/plan")
    public SubscriptionPlanResponse plan() {
        return subscriptionService.getPlan();
    }

    @GetMapping
    public SubscriptionStatusResponse status(Authentication auth) {
        return subscriptionService.getStatus(AuthenticatedMember.id(auth));
    }

    @PostMapping("/checkout")
    public SubscriptionCheckoutResponse checkout(Authentication auth) {
        return subscriptionService.checkout(AuthenticatedMember.id(auth));
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

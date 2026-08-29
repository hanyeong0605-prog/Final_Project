package com.jobpilot.api.domain.subscription.dto;

// 2026-08-29: 이용권 상품이 여러 개(1회/5회/10회)라 어떤 걸 살지 받아야 한다. 알 수 없는
// id가 오면 SubscriptionPlan.findById가 기본 상품으로 떨어뜨리므로 결제가 깨지지는 않는다.
public record SubscriptionCheckoutRequest(String planId) {}

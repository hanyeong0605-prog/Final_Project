package com.jobpilot.api.domain.subscription.dto;

// orderName은 토스 결제창에 표시할 상품명(프론트가 그대로 requestPayment()에 넘긴다).
public record SubscriptionCheckoutResponse(String orderId, int amount, String orderName) {}

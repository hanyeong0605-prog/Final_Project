package com.jobpilot.api.domain.subscription.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

// 결제창 successUrl로 리다이렉트되며 받는 쿼리 파라미터 그대로 - 프론트가 그대로 옮겨 담아 POST한다.
public record SubscriptionConfirmRequest(
        @NotBlank String paymentKey, @NotBlank String orderId, @Min(1) int amount
) {}

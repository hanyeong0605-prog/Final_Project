package com.jobpilot.api.domain.subscription.dto;

import java.time.LocalDateTime;

public record SubscriptionStatusResponse(
        boolean subscribed, String planId, String displayName, int priceWon,
        LocalDateTime currentPeriodEnd, LocalDateTime nextBillingAt
) {}

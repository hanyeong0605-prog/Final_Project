package com.jobpilot.api.domain.subscription.dto;

import java.time.LocalDateTime;

// 2026-08-29: 이용권(횟수) 전환 - subscribed는 이제 "기간 구독 중인가"가 아니라 "실전면접을
// 지금 쓸 수 있는가"(남은 횟수가 있거나 관리자)를 뜻한다. remainingSessions가 실제 잔여 횟수다.
// currentPeriodEnd/nextBillingAt은 기간 구독 시절의 잔재라 항상 null이지만, 구버전 프론트가
// 읽어도 깨지지 않게 필드는 남겨둔다.
public record SubscriptionStatusResponse(
        boolean subscribed, String planId, String displayName, int priceWon,
        LocalDateTime currentPeriodEnd, LocalDateTime nextBillingAt, boolean admin,
        int remainingSessions
) {}

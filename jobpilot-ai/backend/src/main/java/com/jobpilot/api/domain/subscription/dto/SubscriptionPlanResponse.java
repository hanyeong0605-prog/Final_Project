package com.jobpilot.api.domain.subscription.dto;

// 2026-08-29: 이용권 전환 - sessions는 이 상품을 사면 충전되는 실전면접 횟수다.
public record SubscriptionPlanResponse(String planId, String displayName, int priceWon, int sessions) {}

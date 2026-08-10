package com.jobpilot.api.domain.subscription.service;

// 2026-08-10: 구독 기능 - 단일 요금제(사용자가 "단일 요금제로 심플하게" 쪽을 선택). 구독
// 중이면 모의면접 AI 분석 등 유료 기능을 무제한으로 쓸 수 있다(크레딧 잔액 개념 없음).
// 가격은 확정된 정책이 없어서 임시로 넣은 값 - 나중에 바뀌면 이 값만 고치면 된다.
public enum SubscriptionPlan {
    STANDARD("standard", "Job-A-Dream AI 프리미엄 구독", 9_900);

    private final String id;
    private final String displayName;
    private final int priceWon;

    SubscriptionPlan(String id, String displayName, int priceWon) {
        this.id = id;
        this.displayName = displayName;
        this.priceWon = priceWon;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int priceWon() { return priceWon; }

    // 지금은 요금제가 하나뿐이라 항상 이 값을 쓴다 - 여러 등급이 생기면 findById(String)로 바꾸면 됨.
    public static SubscriptionPlan current() { return STANDARD; }
}

package com.jobpilot.api.domain.subscription.service;

import java.util.Arrays;
import java.util.List;

// 2026-08-29: 구독(기간 무제한) → 이용권(횟수) 전환.
//
// 왜 바꿨나 - 토스 자동결제(빌링)를 못 써서 "구독"이 실제로는 1개월 단발 결제였고, 잠기는
// 기능도 실전면접 하나뿐이라 월정액의 명분이 약했다. "한 달에 9,900원"보다 "5번에 5,900원"이
// 쓰는 맥락(면접 잡혔을 때 몰아서 연습)에 맞고, 자동갱신이 없다는 점도 이용권에서는 전혀
// 이상하지 않다.
//
// 1회권을 회당 단가로 제일 비싸게 뒀다 - 부담 없이 한 번 써보게 하되 5회권으로 유도하는
// 구조다. 10회권을 예전 구독가(9,900원)에 맞춰서, 같은 돈으로 열 번이 되게 했다.
public enum SubscriptionPlan {
    SINGLE("single", "실전면접 1회 이용권", 1_500, 1),
    FIVE("five", "실전면접 5회 이용권", 5_900, 5),
    TEN("ten", "실전면접 10회 이용권", 9_900, 10);

    private final String id;
    private final String displayName;
    private final int priceWon;
    private final int sessions;

    SubscriptionPlan(String id, String displayName, int priceWon, int sessions) {
        this.id = id;
        this.displayName = displayName;
        this.priceWon = priceWon;
        this.sessions = sessions;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int priceWon() { return priceWon; }
    /** 이 상품을 결제하면 충전되는 실전면접 횟수. */
    public int sessions() { return sessions; }

    public static List<SubscriptionPlan> all() {
        return Arrays.asList(values());
    }

    /** 알 수 없는 id는 기본 상품으로 떨어뜨린다 - 구버전 프론트가 상품을 안 보내도 결제가
     *  깨지지 않게 하기 위함이다. */
    public static SubscriptionPlan findById(String id) {
        return all().stream().filter(plan -> plan.id.equals(id)).findFirst().orElse(FIVE);
    }

    /** 기본 노출 상품(5회권) - 프론트가 아무것도 안 고른 상태의 기준값. */
    public static SubscriptionPlan current() { return FIVE; }

    /**
     * 결제 금액으로 상품을 되찾는다.
     *
     * subscription_payments에는 금액만 남고 상품 id는 없다(그 테이블을 바꾸지 않기로 했다).
     * 대신 결제 승인 단계에서 이미 "checkout 때 서버가 정한 금액 == 돌아온 금액"을 검증하므로,
     * 이 금액은 클라이언트가 조작할 수 없는 서버 기록값이다 - 상품을 되찾는 근거로 안전하다.
     * 상품 가격은 서로 겹치지 않게 유지해야 한다.
     */
    public static SubscriptionPlan findByPrice(int priceWon) {
        return all().stream().filter(plan -> plan.priceWon == priceWon).findFirst().orElse(current());
    }
}

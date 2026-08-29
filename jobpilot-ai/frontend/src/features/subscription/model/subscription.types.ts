// 2026-08-29: 구독(기간 무제한) → 이용권(횟수) 전환.
export interface SubscriptionPlan {
  planId: string;
  displayName: string;
  priceWon: number;
  /** 이 상품을 사면 충전되는 실전면접 횟수. */
  sessions: number;
}

export interface SubscriptionStatus {
  /** 실전면접을 지금 쓸 수 있는지 - 남은 횟수가 있거나 관리자. */
  subscribed: boolean;
  planId: string | null;
  displayName: string | null;
  priceWon: number;
  /** 기간 구독 시절의 잔재라 항상 null이다. */
  currentPeriodEnd: string | null;
  nextBillingAt: string | null;
  admin: boolean;
  /** 남은 실전면접 횟수. */
  remainingSessions: number;
}

export interface SubscriptionCheckout {
  orderId: string;
  amount: number;
  orderName: string;
}

export interface SubscriptionPlan {
  planId: string;
  displayName: string;
  priceWon: number;
}

export interface SubscriptionStatus {
  subscribed: boolean;
  planId: string | null;
  displayName: string | null;
  priceWon: number;
  currentPeriodEnd: string | null;
  nextBillingAt: string | null;
}

export interface SubscriptionCheckout {
  orderId: string;
  amount: number;
  orderName: string;
}

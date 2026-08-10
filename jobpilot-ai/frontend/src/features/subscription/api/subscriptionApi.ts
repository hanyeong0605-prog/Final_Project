import { getJson, postJson } from "../../../api/httpClient";
import type { SubscriptionCheckout, SubscriptionPlan, SubscriptionStatus } from "../model/subscription.types";

const BASE = "/api/v1/members/me/subscription";

export const getSubscriptionPlan = () => getJson<SubscriptionPlan>(`${BASE}/plan`);
export const getSubscriptionStatus = () => getJson<SubscriptionStatus>(BASE);
export const checkoutSubscription = () => postJson<SubscriptionCheckout>(`${BASE}/checkout`, {});
export const confirmSubscriptionPayment = (paymentKey: string, orderId: string, amount: number) =>
  postJson<SubscriptionStatus>(`${BASE}/confirm`, { paymentKey, orderId, amount });
export const cancelSubscription = () => postJson<SubscriptionStatus>(`${BASE}/cancel`, {});

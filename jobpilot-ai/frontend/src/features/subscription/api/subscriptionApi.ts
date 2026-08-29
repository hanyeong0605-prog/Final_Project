import { getJson, postJson } from "../../../api/httpClient";
import type { SubscriptionCheckout, SubscriptionPlan, SubscriptionStatus } from "../model/subscription.types";

const BASE = "/api/v1/members/me/subscription";

// 2026-08-29: 구독(기간) → 이용권(횟수) 전환. 상품이 1회/5회/10회 세 개라 목록으로 받고,
// checkout에 어떤 상품인지 실어 보낸다.
export const getSubscriptionPlans = () => getJson<SubscriptionPlan[]>(`${BASE}/plans`);
export const getSubscriptionStatus = () => getJson<SubscriptionStatus>(BASE);
export const checkoutSubscription = (planId: string) =>
  postJson<SubscriptionCheckout>(`${BASE}/checkout`, { planId });
export const confirmSubscriptionPayment = (paymentKey: string, orderId: string, amount: number) =>
  postJson<SubscriptionStatus>(`${BASE}/confirm`, { paymentKey, orderId, amount });
export const cancelSubscription = () => postJson<SubscriptionStatus>(`${BASE}/cancel`, {});

// 실전면접 세션 하나를 차감한다. 질문 조립이 성공한 직후에만 부른다 - 시작 버튼 시점에
// 부르면 질문 생성이 전부 실패했는데도 횟수가 날아간다(SubscriptionService.consumeSession 참고).
export const consumeInterviewSession = () => postJson<SubscriptionStatus>(`${BASE}/consume`, {});

// 2026-08-29: 잔여 횟수는 사이드바 배지(AppShell)와 모의면접 화면 두 군데에 동시에 뜨는데,
// 차감/구매가 일어나는 곳은 그 둘이 아니다(질문 조립 직후, 결제 완료 페이지). 상태 관리
// 라이브러리를 새로 들이는 대신 window 이벤트로 "바뀌었다"만 알리고, 듣는 쪽이 다시 조회한다.
export const INTERVIEW_PASS_CHANGED_EVENT = "interview-pass-changed";

export function notifyInterviewPassChanged() {
  window.dispatchEvent(new CustomEvent(INTERVIEW_PASS_CHANGED_EVENT));
}

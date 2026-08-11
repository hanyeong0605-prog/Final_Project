import { useEffect, useState } from "react";
import { Crown } from "lucide-react";
import { cancelSubscription, checkoutSubscription, getSubscriptionPlan, getSubscriptionStatus } from "../api/subscriptionApi";
import type { SubscriptionPlan, SubscriptionStatus } from "../model/subscription.types";
import { useAuth } from "../../auth/model/AuthContext";

// 2026-08-10: 구독 기능 - 처음엔 토스 자동결제(빌링) 카드 등록창(requestBillingAuth)으로
// 만들었는데, 그 API는 테스트 환경에서도 별도 계약이 필요해서 막혔다("테스트 결제만 되면
// 된다" 피드백) - 그래서 계약 없이 문서 테스트 키로 바로 되는 일반 결제창(requestPayment)
// 방식으로 바꿨다. 대신 카드가 저장되지 않아서 "구독하기"를 누르면 매번 결제창이 뜬다 -
// 진짜 무음 자동결제가 아니라 "달마다 다시 결제해야 연장되는" 방식이다(SubscriptionService
// 참고).
declare global {
  interface Window {
    TossPayments?: (clientKey: string) => {
      requestPayment: (method: string, params: Record<string, unknown>) => Promise<void>;
    };
  }
}

export function SubscriptionSection() {
  const { member } = useAuth();
  const [plan, setPlan] = useState<SubscriptionPlan | null>(null);
  const [status, setStatus] = useState<SubscriptionStatus | null>(null);
  const [error, setError] = useState("");
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    void Promise.all([getSubscriptionPlan(), getSubscriptionStatus()])
      .then(([p, s]) => {
        setPlan(p);
        setStatus(s);
      })
      .catch(() => setError("구독 정보를 불러오지 못했습니다."));
  }, []);

  const handleSubscribe = async () => {
    if (!plan) return;
    const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY as string | undefined;
    if (!clientKey || !window.TossPayments) {
      setError("결제 모듈을 불러오지 못했습니다. 새로고침 후 다시 시도해주세요.");
      return;
    }
    setError("");
    setIsBusy(true);
    try {
      const order = await checkoutSubscription();
      const tossPayments = window.TossPayments(clientKey);
      await tossPayments.requestPayment("카드", {
        amount: order.amount,
        orderId: order.orderId,
        orderName: order.orderName,
        customerName: member?.nickname,
        customerEmail: member?.email,
        successUrl: `${window.location.origin}/subscription/success`,
        failUrl: `${window.location.origin}/subscription/fail`,
      });
      // 성공 시 토스가 successUrl로 리다이렉트하므로 이 아래 코드는 실행되지 않는다.
    } catch (e) {
      setIsBusy(false);
      const message = e instanceof Error ? e.message : "";
      if (!message.includes("CLOSE") && !message.includes("취소")) {
        setError("결제 요청 중 오류가 발생했습니다.");
      }
    }
  };

  const handleCancel = async () => {
    if (!confirm("구독을 해지할까요? 해지 즉시 프리미엄 기능 이용이 중단됩니다.")) return;
    setError("");
    setIsBusy(true);
    try {
      const s = await cancelSubscription();
      setStatus(s);
    } catch {
      setError("구독 해지 중 오류가 발생했습니다.");
    } finally {
      setIsBusy(false);
    }
  };

  const isSubscribed = status?.subscribed ?? false;

  return (
    <section className="panel subscription-section">
      <div className="panel-title">
        <div>
          <h2><Crown size={16} style={{ verticalAlign: "-3px", marginRight: "6px" }} />구독</h2>
          <p>모의면접 AI 분석 등 유료 기능을 무제한으로 이용합니다.</p>
        </div>
        <span className={`subscription-badge${isSubscribed ? " active" : ""}`}>
          {isSubscribed ? "구독 중" : "미구독"}
        </span>
      </div>

      {error && <div className="account-alert error">{error}</div>}

      {isSubscribed ? (
        <>
          <div className="subscription-summary">
            <div><span>요금제</span><strong>{status?.displayName}</strong></div>
            <div><span>월 결제 금액</span><strong>{status?.priceWon.toLocaleString()}원</strong></div>
            <div><span>이용 만료일</span><strong>{status?.currentPeriodEnd?.slice(0, 10) ?? "-"}</strong></div>
          </div>
          <button className="danger-button" disabled={isBusy} onClick={() => void handleCancel()}>
            {isBusy ? "처리 중..." : "구독 해지"}
          </button>
        </>
      ) : (
        <>
          {plan && (
            <div className="subscription-summary">
              <div><span>요금제</span><strong>{plan.displayName}</strong></div>
              <div><span>월 결제 금액</span><strong>{plan.priceWon.toLocaleString()}원</strong></div>
            </div>
          )}
          <button className="primary-button" disabled={!plan || isBusy} onClick={() => void handleSubscribe()}>
            {isBusy ? "결제창 여는 중..." : "구독하기"}
          </button>
        </>
      )}
    </section>
  );
}

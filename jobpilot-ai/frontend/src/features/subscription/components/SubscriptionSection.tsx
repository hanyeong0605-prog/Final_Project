import { useEffect, useState } from "react";
import { Crown } from "lucide-react";
import { cancelSubscription, checkoutSubscription, getSubscriptionPlans, getSubscriptionStatus } from "../api/subscriptionApi";
import type { SubscriptionPlan, SubscriptionStatus } from "../model/subscription.types";
import { useAuth } from "../../auth/model/AuthContext";

// 2026-08-10: 처음엔 토스 자동결제(빌링) 카드 등록창(requestBillingAuth)으로 만들었는데,
// 그 API는 테스트 환경에서도 별도 계약이 필요해서 막혔다("테스트 결제만 되면 된다" 피드백) -
// 그래서 계약 없이 문서 테스트 키로 바로 되는 일반 결제창(requestPayment) 방식으로 바꿨다.
//
// 2026-08-29: 그 제약 때문에 "구독"이 실제로는 1개월 단발 결제였고, 잠기는 기능도 실전면접
// 하나뿐이라 월정액의 명분이 약했다. 산 만큼 쓰는 이용권(1회/5회/10회)으로 바꿨다 -
// 자동결제가 없다는 점이 이용권에서는 전혀 이상하지 않고, 유효기간도 두지 않는다.
declare global {
  interface Window {
    TossPayments?: (clientKey: string) => {
      requestPayment: (method: string, params: Record<string, unknown>) => Promise<void>;
    };
  }
}

export function SubscriptionSection() {
  const { member } = useAuth();
  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [status, setStatus] = useState<SubscriptionStatus | null>(null);
  const [error, setError] = useState("");
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    void Promise.all([getSubscriptionPlans(), getSubscriptionStatus()])
      .then(([p, s]) => {
        setPlans(p);
        // 기본값은 가운데 상품(5회권) - 회당 단가와 부담이 균형점이다.
        setSelectedPlanId(p[Math.min(1, p.length - 1)]?.planId ?? null);
        setStatus(s);
      })
      .catch(() => setError("이용권 정보를 불러오지 못했습니다."));
  }, []);

  const selectedPlan = plans.find((p) => p.planId === selectedPlanId) ?? null;

  const handlePurchase = async () => {
    if (!selectedPlan) return;
    const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY as string | undefined;
    if (!clientKey || !window.TossPayments) {
      setError("결제 모듈을 불러오지 못했습니다. 새로고침 후 다시 시도해주세요.");
      return;
    }
    setError("");
    setIsBusy(true);
    try {
      const order = await checkoutSubscription(selectedPlan.planId);
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
    if (!confirm("남은 이용권을 모두 버리고 해지할까요? 이 동작은 되돌릴 수 없습니다.")) return;
    setError("");
    setIsBusy(true);
    try {
      const s = await cancelSubscription();
      setStatus(s);
    } catch {
      setError("해지 중 오류가 발생했습니다.");
    } finally {
      setIsBusy(false);
    }
  };

  const remaining = status?.remainingSessions ?? 0;
  // 2026-08-13: admin=true는 "관리자라 결제 없이 무제한 이용 중"인 placeholder 상태에서만
  // 온다(SubscriptionService.toResponse는 실제 이용권이면 항상 admin:false로 내려준다).
  const isAdminPlaceholder = status?.admin ?? false;

  return (
    <section className="panel subscription-section">
      <div className="panel-title">
        <div>
          <h2><Crown size={16} style={{ verticalAlign: "-3px", marginRight: "6px" }} />실전면접 이용권</h2>
          <p>산 만큼 쓰는 횟수제예요. 유효기간이 없어서 남은 횟수는 그대로 유지됩니다.</p>
        </div>
        <span className={`subscription-badge${remaining > 0 || isAdminPlaceholder ? " active" : ""}`}>
          {isAdminPlaceholder ? "관리자(무제한)" : `${remaining}회 남음`}
        </span>
      </div>

      {error && <div className="account-alert error">{error}</div>}

      {isAdminPlaceholder && (
        <p className="account-alert" style={{ marginBottom: 12 }}>
          관리자 계정은 결제 없이 모든 기능을 이용할 수 있어요. 아래에서 실제 구매 흐름을
          테스트해볼 수 있습니다(토스 테스트 키로 진행되어 실제 결제는 되지 않아요).
        </p>
      )}

      {!isAdminPlaceholder && (
        <p className="account-alert" style={{ marginBottom: 12 }}>
          매달 <strong>무료 1회</strong>가 지급돼요. 모의면접(코퍼스 질문)은 이용권 없이 계속 이용할 수 있습니다.
        </p>
      )}

      <div className="interview-option-row" style={{ marginBottom: 14 }}>
        {plans.map((plan) => (
          <button
            key={plan.planId}
            type="button"
            className={`interview-option-chip${selectedPlanId === plan.planId ? " active" : ""}`}
            style={{ flexDirection: "column", gap: 2, padding: "12px 10px" }}
            onClick={() => setSelectedPlanId(plan.planId)}
          >
            <strong>{plan.sessions}회</strong>
            <span style={{ fontSize: 12 }}>{plan.priceWon.toLocaleString()}원</span>
            <span style={{ fontSize: 11, opacity: 0.75 }}>회당 {Math.round(plan.priceWon / plan.sessions).toLocaleString()}원</span>
          </button>
        ))}
      </div>

      <button className="primary-button" disabled={!selectedPlan || isBusy} onClick={() => void handlePurchase()}>
        {isBusy
          ? "결제창 여는 중..."
          : selectedPlan
            ? `${selectedPlan.sessions}회 이용권 구매 (${selectedPlan.priceWon.toLocaleString()}원)`
            : "이용권 구매"}
      </button>

      {remaining > 0 && !isAdminPlaceholder && (
        <button
          className="danger-button"
          style={{ marginTop: 10 }}
          disabled={isBusy}
          onClick={() => void handleCancel()}
        >
          {isBusy ? "처리 중..." : "이용권 해지"}
        </button>
      )}
    </section>
  );
}

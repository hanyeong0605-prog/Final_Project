import { useEffect, useState } from "react";
import { useLocation, useSearchParams, Link } from "react-router-dom";
import { CheckCircle2, XCircle } from "lucide-react";
import { confirmSubscriptionPayment, notifyInterviewPassChanged } from "../features/subscription/api/subscriptionApi";
import { PageHeading } from "../shared/components/PageHeading";
import { DataStatePanel } from "../shared/components/DataStatePanel";

// 2026-08-10: 구독 기능 - 토스 결제창이 성공/실패 시 각각 successUrl(/subscription/success)·
// failUrl(/subscription/fail)로 쿼리 파라미터를 붙여 리다이렉트한다(SubscriptionSection.tsx
// 참고). 성공 시엔 여기서 백엔드 confirm API를 호출해야 결제 승인 + 구독 활성화가 실제로
// 일어난다 - 리다이렉트 자체는 아직 확정이 아니다(토스 결제창 가이드 3단계와 동일).
export function SubscriptionResultPage() {
  const location = useLocation();
  const isSuccess = location.pathname.endsWith("/success");
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState<"loading" | "done" | "error">(isSuccess ? "loading" : "error");
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (!isSuccess) {
      setMessage(searchParams.get("message") || "결제가 취소되었거나 실패했습니다.");
      return;
    }
    const paymentKey = searchParams.get("paymentKey");
    const orderId = searchParams.get("orderId");
    const amount = Number(searchParams.get("amount"));
    if (!paymentKey || !orderId || !amount) {
      setStatus("error");
      setMessage("결제 정보가 올바르지 않습니다.");
      return;
    }
    confirmSubscriptionPayment(paymentKey, orderId, amount)
      .then(() => {
        setStatus("done");
        // 구매 직후 사이드바 배지가 즉시 새 잔여 횟수를 보여주도록 알린다.
        notifyInterviewPassChanged();
      })
      .catch((e) => {
        setStatus("error");
        setMessage(e instanceof Error ? e.message : "결제 확정에 실패했습니다.");
      });
  }, [isSuccess, searchParams]);

  return (
    <>
      <PageHeading eyebrow="INTERVIEW PASS" title="결제 결과" body="실전면접 이용권 결제 결과를 확인합니다." />
      <div style={{ padding: "40px 24px", background: "#ffffff", borderRadius: "12px", border: "1px solid #e2e8f0", textAlign: "center" }}>
        {status === "loading" && <DataStatePanel state="loading" />}
        {status === "done" && (
          <>
            <CheckCircle2 size={40} color="#16a34a" />
            <p style={{ marginTop: "16px", fontWeight: 600 }}>실전면접 이용권이 충전되었습니다.</p>
          </>
        )}
        {status === "error" && (
          <>
            <XCircle size={40} color="#dc2626" />
            <p style={{ marginTop: "16px", fontWeight: 600 }}>{message}</p>
          </>
        )}
        {status !== "loading" && (
          <Link to="/account?tab=subscription" className="primary-button" style={{ display: "inline-block", marginTop: "20px", textDecoration: "none" }}>
            마이페이지로 이동
          </Link>
        )}
      </div>
    </>
  );
}

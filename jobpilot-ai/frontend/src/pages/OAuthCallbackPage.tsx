import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../features/auth/model/AuthContext";

export function OAuthCallbackPage() {
  const { member, completeExternalLogin } = useAuth();
  const [error, setError] = useState("");

  useEffect(() => {
    const token = new URLSearchParams(window.location.hash.slice(1)).get("access_token");
    if (!token) { setError("소셜 로그인 토큰을 받지 못했습니다. 다시 시도해 주세요."); return; }
    void completeExternalLogin(token).catch((reason) => setError(reason instanceof Error ? reason.message : "로그인 처리에 실패했습니다."));
  }, [completeExternalLogin]);

  if (member) return <Navigate to="/" replace />;
  return <main className="auth-page"><section className="auth-card oauth-status-card"><span className="eyebrow">SOCIAL LOGIN</span><h1>로그인 처리 중</h1><p>{error || "계정을 안전하게 연결하고 있습니다."}</p>{error && <a className="primary-button" href="/login">로그인으로 돌아가기</a>}</section></main>;
}

import { FormEvent, useState } from "react";
import { Link, Navigate, useSearchParams } from "react-router-dom";
import {
  completeOAuthSignup,
  confirmEmailVerificationCode,
  sendEmailVerificationCode,
} from "../features/auth/api/authApi";
import { useAuth } from "../features/auth/model/AuthContext";

export function OAuthCompletePage() {
  const { member, completeExternalLogin } = useAuth();
  const [params] = useSearchParams();
  const ticket = params.get("ticket") ?? "";
  const provider = params.get("provider") ?? "social";
  const requestedReturnTo = params.get("returnTo");
  const returnTo = requestedReturnTo?.startsWith("/camera-pair?") || requestedReturnTo?.startsWith("/admin-face-pair?")
    ? requestedReturnTo
    : "/";
  const providerEmail = params.get("email") ?? "";
  const trustsProviderEmail = Boolean(providerEmail);
  const [email, setEmail] = useState(providerEmail);
  const [code, setCode] = useState("");
  const [verificationToken, setVerificationToken] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [termsAgreed, setTermsAgreed] = useState(false);
  const [privacyCollectionAgreed, setPrivacyCollectionAgreed] = useState(false);
  const [marketingEmailAgreed, setMarketingEmailAgreed] = useState(false);

  if (member) return <Navigate to={returnTo} replace />;
  if (!ticket) return <Navigate to="/login" replace />;

  const sendCode = async () => {
    setError("");
    setMessage("");
    try {
      await sendEmailVerificationCode(email);
      setMessage("인증코드를 이메일로 보냈습니다.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "인증코드 발송에 실패했습니다.");
    }
  };

  const verifyCode = async () => {
    setError("");
    setMessage("");
    try {
      const result = await confirmEmailVerificationCode(email, code);
      setVerificationToken(result.verificationToken);
      setMessage("이메일 인증이 완료되었습니다.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "인증코드가 올바르지 않습니다.");
    }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      const result = await completeOAuthSignup({
        ticket,
        email,
        emailVerificationToken: verificationToken,
        termsAgreed,
        privacyCollectionAgreed,
        marketingEmailAgreed,
      });
      await completeExternalLogin(result.accessToken);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "계정 연결에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="auth-page">
      <section className="auth-card oauth-complete-card">
        <span className="eyebrow">{provider.toUpperCase()} LOGIN</span>
        <h1>{trustsProviderEmail ? "소셜 계정을 확인했어요" : "이메일을 확인해주세요"}</h1>
        <p>
          {trustsProviderEmail
            ? "소셜 계정에서 확인된 이메일입니다. 필수 약관에 동의하면 바로 가입이 완료됩니다."
            : "간편 로그인 계정을 안전하게 식별하고 기존 계정과 연결하기 위해 이메일 인증이 필요합니다."}
        </p>
        <form onSubmit={submit}>
          <label>
            이메일
            <input
              required
              type="email"
              value={email}
              readOnly={trustsProviderEmail}
              onChange={(event) => {
                setEmail(event.target.value);
                setVerificationToken("");
              }}
            />
          </label>
          {!trustsProviderEmail && (
            <>
              <button type="button" className="outline-button" onClick={() => void sendCode()}>
                인증코드 보내기
              </button>
              <label>
                이메일 인증코드
                <input required value={code} onChange={(event) => setCode(event.target.value)} />
              </label>
              <button
                type="button"
                className="outline-button"
                onClick={() => void verifyCode()}
                disabled={!email || !code}
              >
                코드 확인
              </button>
            </>
          )}
          <label className="oauth-consent">
            <input type="checkbox" checked={termsAgreed} onChange={(event) => setTermsAgreed(event.target.checked)} />
            서비스 이용약관에 동의합니다. (필수)
          </label>
          <label className="oauth-consent">
            <input
              type="checkbox"
              checked={privacyCollectionAgreed}
              onChange={(event) => setPrivacyCollectionAgreed(event.target.checked)}
            />
            개인정보 수집·이용에 동의합니다. (필수)
          </label>
          <label className="oauth-consent">
            <input
              type="checkbox"
              checked={marketingEmailAgreed}
              onChange={(event) => setMarketingEmailAgreed(event.target.checked)}
            />
            이메일 마케팅 수신에 동의합니다. (선택)
          </label>
          {message && <div className="auth-message">{message}</div>}
          {error && <div className="auth-error">{error}</div>}
          <button
            className="primary-button"
            disabled={(!trustsProviderEmail && !verificationToken) || !termsAgreed || !privacyCollectionAgreed || submitting}
          >
            {submitting ? "계정 연결 중..." : "간편가입 완료"}
          </button>
        </form>
        <div className="auth-switch">
          <Link to="/login">로그인으로 돌아가기</Link>
        </div>
      </section>
    </main>
  );
}

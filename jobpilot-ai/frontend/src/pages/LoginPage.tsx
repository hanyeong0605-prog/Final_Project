import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../features/auth/model/AuthContext";
import { AccountTypeToggle } from "../shared/components/AccountTypeToggle";

// Vite's DEV is true only for `npm run dev`; production builds never show this button.
const developmentLoginEnabled = import.meta.env.DEV;

export function LoginPage() {
  const { member, login, developmentLogin, developmentAdminLogin } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const requestedReturnTo = params.get("returnTo");
  const socialError = params.get("socialError");
  // Preserve only an internal phone-pairing route after login.
  const returnTo = requestedReturnTo?.startsWith("/camera-pair?") ? requestedReturnTo : "/";
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [developmentSigningIn, setDevelopmentSigningIn] = useState(false);
  const [developmentAdminSigningIn, setDevelopmentAdminSigningIn] = useState(false);

  if (member) return <Navigate to={returnTo} replace />;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);

    try {
      await login({ loginId, password });
      navigate(returnTo);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "로그인에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const signInAsDevelopmentMember = async () => {
    setError("");
    setDevelopmentSigningIn(true);

    try {
      await developmentLogin();
      navigate(returnTo);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "개발 계정 로그인에 실패했습니다.");
    } finally {
      setDevelopmentSigningIn(false);
    }
  };

  // 관리자 페이지 테스트용 - 로컬 전용 계정을 만들어 ADMIN 권한으로 바로 들어간다.
  // 기존 "개발 계정으로 바로 입장"(local-dev, 일반 회원)과는 별도 계정이라 권한 테스트에 영향 없다.
  const signInAsDevelopmentAdmin = async () => {
    setError("");
    setDevelopmentAdminSigningIn(true);

    try {
      await developmentAdminLogin();
      navigate("/admin");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "관리자 계정 로그인에 실패했습니다.");
    } finally {
      setDevelopmentAdminSigningIn(false);
    }
  };

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-brand">
          <span className="brand-mark"><span>J</span></span>
          <div>
            <strong>Job-A-Dream AI</strong>
            <small>career action coach</small>
          </div>
        </div>
        <AccountTypeToggle value="member" memberTo="/login" employerTo="/employer/login" />
        <span className="eyebrow">WELCOME BACK</span>
        <h1>로그인</h1>
        <p>맞춤형 채용공고와 분석 결과를 확인하세요.</p>
        <form onSubmit={submit}>
          <label>
            아이디
            <input required value={loginId} onChange={(event) => setLoginId(event.target.value)} autoComplete="username" />
          </label>
          <label>
            비밀번호
            <input required type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" />
          </label>
          {error && <div className="auth-error">{error}</div>}
          <button className="primary-button" disabled={submitting || developmentSigningIn}>
            {submitting ? "로그인 중..." : "로그인"}
          </button>
        </form>

        {socialError && (
          <div className="auth-error">
            소셜 로그인 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.
          </div>
        )}

        <div className="oauth-login-separator"><span>간편 로그인</span></div>
        <div className="oauth-login-buttons" aria-label="소셜 로그인">
          <a className="oauth-login-button google" href="/oauth2/authorization/google">Google로 계속하기</a>
          <a className="oauth-login-button naver" href="/oauth2/authorization/naver">NAVER로 계속하기</a>
          <a className="oauth-login-button kakao" href="/oauth2/authorization/kakao">카카오로 계속하기</a>
        </div>

        {developmentLoginEnabled && (
          <>
            <div className="dev-login-separator"><span>로컬 개발 모드</span></div>
            <button
              type="button"
              className="development-login-button"
              onClick={signInAsDevelopmentMember}
              disabled={submitting || developmentSigningIn || developmentAdminSigningIn}
            >
              {developmentSigningIn ? "개발 계정으로 입장 중..." : "개발 계정으로 바로 입장"}
            </button>
            <button
              type="button"
              className="development-login-button admin"
              onClick={signInAsDevelopmentAdmin}
              disabled={submitting || developmentSigningIn || developmentAdminSigningIn}
            >
              {developmentAdminSigningIn ? "관리자 계정으로 입장 중..." : "관리자 계정으로 바로 입장"}
            </button>
          </>
        )}

        <div className="auth-switch">계정이 없나요? <Link to="/signup">회원가입</Link></div>
      </section>
    </main>
  );
}

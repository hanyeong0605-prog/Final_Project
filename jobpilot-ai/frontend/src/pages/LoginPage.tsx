import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../features/auth/model/AuthContext";

// Vite's DEV is true only for `npm run dev`; production builds never show this button.
const developmentLoginEnabled = import.meta.env.DEV;

export function LoginPage() {
  const { member, login, developmentLogin } = useAuth();
  const navigate = useNavigate();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [developmentSigningIn, setDevelopmentSigningIn] = useState(false);

  if (member) return <Navigate to="/" replace />;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);

    try {
      await login({ loginId, password });
      navigate("/");
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
      navigate("/");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "개발 계정 로그인에 실패했습니다.");
    } finally {
      setDevelopmentSigningIn(false);
    }
  };

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-brand">
          <span className="brand-mark"><span>J</span></span>
          <div>
            <strong>JobPilot AI</strong>
            <small>career action coach</small>
          </div>
        </div>
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

        {developmentLoginEnabled && (
          <>
            <div className="dev-login-separator"><span>로컬 개발 모드</span></div>
            <button
              type="button"
              className="development-login-button"
              onClick={signInAsDevelopmentMember}
              disabled={submitting || developmentSigningIn}
            >
              {developmentSigningIn ? "개발 계정으로 입장 중..." : "개발 계정으로 바로 입장"}
            </button>
          </>
        )}

        <div className="auth-switch">계정이 없나요? <Link to="/signup">회원가입</Link></div>
      </section>
    </main>
  );
}

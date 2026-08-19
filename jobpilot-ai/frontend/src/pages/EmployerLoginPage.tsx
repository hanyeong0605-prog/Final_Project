import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useEmployerAuth } from "../features/employer/model/EmployerAuthContext";

export function EmployerLoginPage() {
  const { employer, login } = useEmployerAuth();
  const navigate = useNavigate();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (employer) return <Navigate to="/employer" replace />;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      await login({ loginId, password });
      navigate("/employer");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "로그인에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-brand">
          <span className="brand-mark"><span>J</span></span>
          <div>
            <strong>Job-A-Dream AI</strong>
            <small>기업회원 채용 관리</small>
          </div>
        </div>
        <span className="eyebrow">EMPLOYER LOGIN</span>
        <h1>기업회원 로그인</h1>
        <p>승인된 기업회원은 채용공고를 직접 등록·관리할 수 있어요.</p>
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
          <button className="primary-button" disabled={submitting}>{submitting ? "로그인 중..." : "로그인"}</button>
        </form>

        <div className="auth-switch">기업회원이 아니신가요? <Link to="/login">개인회원으로 로그인</Link></div>
        <div className="auth-switch">아직 가입 전이신가요? <Link to="/employer/signup">기업회원 가입</Link></div>
      </section>
    </main>
  );
}

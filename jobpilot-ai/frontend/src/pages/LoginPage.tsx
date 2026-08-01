import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../features/auth/model/AuthContext";

export function LoginPage() {
  const { member, login } = useAuth();
  const navigate = useNavigate();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  if (member) return <Navigate to="/" replace />;

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError(""); setSubmitting(true);
    try { await login({ loginId, password }); navigate("/"); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "로그인에 실패했습니다."); }
    finally { setSubmitting(false); }
  };

  return <main className="auth-page"><section className="auth-card"><div className="auth-brand"><span className="brand-mark"><span>J</span></span><div><strong>JobPilot AI</strong><small>career action coach</small></div></div><span className="eyebrow">WELCOME BACK</span><h1>로그인</h1><p>내 스펙과 사람인 채용공고의 분석 결과를 확인하세요.</p><form onSubmit={submit}><label>아이디<input required value={loginId} onChange={(e) => setLoginId(e.target.value)} autoComplete="username" /></label><label>비밀번호<input required type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></label>{error && <div className="auth-error">{error}</div>}<button className="primary-button" disabled={submitting}>{submitting ? "로그인 중..." : "로그인"}</button></form><div className="auth-switch">계정이 없나요? <Link to="/signup">회원가입</Link></div></section></main>;
}

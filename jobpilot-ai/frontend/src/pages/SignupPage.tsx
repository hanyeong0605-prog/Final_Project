import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../features/auth/model/AuthContext";

export function SignupPage() {
  const { member, signup } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ loginId: "", email: "", password: "", nickname: "" });
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  if (member) return <Navigate to="/" replace />;
  const update = (field: keyof typeof form, value: string) => setForm((current) => ({ ...current, [field]: value }));

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError(""); setSubmitting(true);
    try { await signup(form); navigate("/"); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "회원가입에 실패했습니다."); }
    finally { setSubmitting(false); }
  };

  return <main className="auth-page"><section className="auth-card"><div className="auth-brand"><span className="brand-mark"><span>J</span></span><div><strong>JobPilot AI</strong><small>career action coach</small></div></div><span className="eyebrow">CREATE ACCOUNT</span><h1>회원가입</h1><p>계정을 만든 뒤 목표 직무와 역량 근거를 등록할 수 있습니다.</p><form onSubmit={submit}><label>아이디<input required minLength={4} value={form.loginId} onChange={(e) => update("loginId", e.target.value)} autoComplete="username" placeholder="영문·숫자 4자 이상" /></label><label>이메일<input required type="email" value={form.email} onChange={(e) => update("email", e.target.value)} autoComplete="email" /></label><label>닉네임<input required minLength={2} value={form.nickname} onChange={(e) => update("nickname", e.target.value)} /></label><label>비밀번호<input required type="password" minLength={8} maxLength={72} value={form.password} onChange={(e) => update("password", e.target.value)} autoComplete="new-password" placeholder="8자 이상" /></label>{error && <div className="auth-error">{error}</div>}<button className="primary-button" disabled={submitting}>{submitting ? "가입 중..." : "회원가입"}</button></form><div className="auth-switch">이미 계정이 있나요? <Link to="/login">로그인</Link></div></section></main>;
}

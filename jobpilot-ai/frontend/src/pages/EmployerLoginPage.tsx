import { FormEvent, useEffect, useRef, useState } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { cancelPasswordless, passwordlessResult, startPasswordless } from "../features/employer/api/employerAuthApi";
import { useEmployerAuth } from "../features/employer/model/EmployerAuthContext";
import type { EmployerAuthResponse } from "../features/employer/model/employer.types";
import { AccountTypeToggle } from "../shared/components/AccountTypeToggle";

export function EmployerLoginPage() {
  const { employer, login, acceptPasswordlessAuth } = useEmployerAuth();
  const navigate = useNavigate(); const [searchParams] = useSearchParams();
  const polling = useRef<number>(); const countdown = useRef<number>(); const busy = useRef(false);
  const [mode, setMode] = useState<"password" | "passwordless">("password");
  const [loginId, setLoginId] = useState(""); const [password, setPassword] = useState("");
  const [sessionId, setSessionId] = useState(""); const [servicePassword, setServicePassword] = useState("");
  const [remaining, setRemaining] = useState(0); const [term, setTerm] = useState(60);
  const [loading, setLoading] = useState(false); const [message, setMessage] = useState(""); const [error, setError] = useState("");
  const stop = () => { if (polling.current) clearInterval(polling.current); if (countdown.current) clearInterval(countdown.current); polling.current = undefined; countdown.current = undefined; };
  useEffect(() => stop, []);
  if (employer) return <Navigate to="/employer" replace />;

  const passwordLogin = async (event: FormEvent) => {
    event.preventDefault(); setLoading(true); setError("");
    try { await login({ loginId: loginId.trim(), password }); navigate("/employer"); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "로그인에 실패했습니다."); }
    finally { setLoading(false); }
  };
  const check = async (id: string, sid: string) => {
    if (busy.current) return; busy.current = true;
    try { const result = await passwordlessResult(id, sid); if ("accessToken" in result) { stop(); acceptPasswordlessAuth(result as EmployerAuthResponse); navigate("/employer"); } }
    catch (reason) { stop(); setSessionId(""); setError(reason instanceof Error ? reason.message : "인증 결과 확인에 실패했습니다."); }
    finally { busy.current = false; }
  };
  const start = async () => {
    const id = loginId.trim(); if (!id) { setError("기업회원 아이디를 입력해 주세요."); return; }
    stop(); setLoading(true); setError(""); setMessage("Passwordless 인증 요청 중...");
    try {
      const response = await startPasswordless(id); const seconds = Number(response.data?.term || 60);
      setSessionId(response.sessionId); setServicePassword(response.data?.servicePassword || ""); setTerm(seconds); setRemaining(seconds); setMessage("모바일 앱에서 인증을 승인해 주세요.");
      void check(id, response.sessionId); polling.current = window.setInterval(() => void check(id, response.sessionId), 1500);
      countdown.current = window.setInterval(() => setRemaining((value) => { if (value <= 1) { stop(); setSessionId(""); setMessage("인증 시간이 만료되었습니다. 다시 시도해 주세요."); return 0; } return value - 1; }), 1000);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "인증 요청에 실패했습니다."); setMessage(""); }
    finally { setLoading(false); }
  };
  const cancel = async () => { stop(); if (sessionId) await cancelPasswordless(loginId.trim(), sessionId).catch(() => undefined); setSessionId(""); setRemaining(0); setMessage("인증을 취소했습니다."); };
  const switchMode = (next: "password" | "passwordless") => { stop(); setSessionId(""); setError(""); setMessage(""); setMode(next); };
  const progress = term ? Math.max(0, remaining / term * 100) : 0;

  return <main className="auth-page"><section className="auth-card employer-login-card">
    <div className="auth-brand"><span className="brand-mark"><span>J</span></span><div><strong>Job-A-Dream AI</strong><small>기업회원 채용 관리</small></div></div>
    <AccountTypeToggle value="employer" memberTo="/login" employerTo="/employer/login" />
    <span className="eyebrow">EMPLOYER LOGIN</span><h1>기업회원 로그인</h1><p>{mode === "password" ? "아이디와 비밀번호로 로그인하거나 Passwordless 로그인을 선택하세요." : "등록된 X1280 모바일 앱에서 인증을 승인해 주세요."}</p>
    {searchParams.get("signup") === "pending" && <div className="auth-success">가입 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다.</div>}
    {searchParams.get("enrollment") === "complete" && <div className="auth-success">Passwordless 전환이 완료되었습니다. 앞으로 Passwordless로 로그인해 주세요.</div>}
    <div className="employer-login-mode"><button type="button" className={mode === "password" ? "active" : ""} onClick={() => switchMode("password")}>아이디·비밀번호</button><button type="button" className={mode === "passwordless" ? "active" : ""} onClick={() => switchMode("passwordless")}>Passwordless</button></div>
    {mode === "password" ? <form onSubmit={passwordLogin}><label>아이디<input required value={loginId} onChange={(event) => setLoginId(event.target.value)} autoComplete="username" /></label><label>비밀번호<input required type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" /></label>{error && <div className="auth-error">{error}</div>}<button className="primary-button" disabled={loading}>{loading ? "로그인 중..." : "로그인"}</button></form> : <div className="employer-passwordless-form"><label>기업회원 아이디<input required value={loginId} disabled={loading || Boolean(sessionId)} onChange={(event) => setLoginId(event.target.value)} onKeyDown={(event) => event.key === "Enter" && !sessionId && void start()} /></label>{message && <div className="auth-success">{message}</div>}{error && <div className="auth-error">{error}</div>}{!sessionId ? <button className="primary-button" disabled={loading} onClick={() => void start()}>{loading ? "인증 요청 중..." : "Passwordless 로그인"}</button> : <div className="passwordless-waiting"><small>앱 인증번호</small><strong>{servicePassword || "------"}</strong><span>남은 시간 {remaining}초</span><div><i style={{ width: `${progress}%` }} /></div><button className="outline-button" onClick={() => void cancel()}>인증 취소</button></div>}</div>}
    <div className="employer-auth-links"><div>기기 등록이 필요하신가요? <Link to="/employer/enrollment">Passwordless 등록</Link></div><div>아직 가입 전이신가요? <Link to="/employer/signup">기업회원 가입</Link></div></div>
  </section></main>;
}

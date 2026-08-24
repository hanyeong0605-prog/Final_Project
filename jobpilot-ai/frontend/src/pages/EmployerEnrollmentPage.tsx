import { useEffect, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { Link, useNavigate } from "react-router-dom";
import { checkEnrollment, requestEnrollment } from "../features/employer/api/employerAuthApi";

function qrValue(data: unknown) {
  if (typeof data === "string") return data;
  if (!data || typeof data !== "object") return "";
  const value = data as Record<string, unknown>;
  for (const key of ["joinApUrl", "qrUrl", "url", "qr", "data"]) if (typeof value[key] === "string") return value[key] as string;
  return JSON.stringify(data);
}

export function EmployerEnrollmentPage() {
  const navigate = useNavigate();
  const timer = useRef<number | undefined>(undefined);
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [qr, setQr] = useState("");
  const [loading, setLoading] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => () => { if (timer.current) clearInterval(timer.current); }, []);

  const begin = async () => {
    setLoading(true); setError("");
    try {
      const input = { loginId: loginId.trim(), password, passwordlessConsent: agreed };
      const response = await requestEnrollment(input);
      if (response.registered) { navigate("/employer/login?enrollment=complete"); return; }
      const value = qrValue(response.data);
      if (!value) throw new Error("X1280 QR 데이터가 응답되지 않았습니다.");
      setQr(value);
      timer.current = window.setInterval(async () => {
        try { const status = await checkEnrollment(input); if (status.registered) { if (timer.current) clearInterval(timer.current); navigate("/employer/login?enrollment=complete"); } } catch { /* 다음 폴링에서 재시도 */ }
      }, 2000);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Passwordless 등록 요청에 실패했습니다."); }
    finally { setLoading(false); }
  };

  return <main className="auth-page"><section className="auth-card">
    <div className="auth-brand"><span className="brand-mark"><span>J</span></span><div><strong>Job-A-Dream AI</strong><small>기업회원 Passwordless</small></div></div>
    <span className="eyebrow">X1280 ENROLLMENT</span><h1>Passwordless 전환</h1><p>관리자 승인이 완료된 기업회원만 등록할 수 있습니다.</p>
    {!qr ? <><div className="passwordless-consent-notice"><strong>전환 전에 꼭 확인해 주세요.</strong><p>Passwordless 등록이 완료되면 기존 아이디·비밀번호 로그인은 사용할 수 없으며, 이후에는 등록한 모바일 기기로만 로그인합니다.</p></div><label>기업회원 아이디<input value={loginId} onChange={(event) => setLoginId(event.target.value)} /></label><label>현재 비밀번호 확인<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label><label className="passwordless-consent-check"><input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} /><span>비밀번호 로그인이 비활성화되고 Passwordless 방식으로 전환되는 것에 동의합니다.</span></label><button className="primary-button" disabled={loading || !loginId || !password || !agreed} onClick={() => void begin()}>{loading ? "QR 요청 중..." : "등록 QR 생성"}</button></> : <div className="passwordless-qr"><QRCodeSVG value={qr} size={220} /><strong>X1280 앱으로 QR을 스캔해 주세요.</strong><span>등록 완료 여부를 자동으로 확인하고 있습니다.</span></div>}
    {error && <div className="auth-error">{error}</div>}<div className="auth-switch"><Link to="/employer/login">기업회원 로그인으로 돌아가기</Link></div>
  </section></main>;
}

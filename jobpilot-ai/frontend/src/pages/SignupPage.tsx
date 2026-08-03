import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { checkLoginIdAvailability, confirmEmailVerificationCode, sendEmailVerificationCode } from "../features/auth/api/authApi";
import { useAuth } from "../features/auth/model/AuthContext";

/*테스트*/

type TextField = "loginId" | "email" | "password" | "nickname";
type ConsentField = "termsAgreed" | "privacyCollectionAgreed" | "marketingEmailAgreed";

export function SignupPage() {
  const { member, signup } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    loginId: "",
    email: "",
    emailVerificationToken: "",
    password: "",
    nickname: "",
    termsAgreed: false,
    privacyCollectionAgreed: false,
    marketingEmailAgreed: false,
  });
  const [emailCode, setEmailCode] = useState("");
  const [loginIdAvailable, setLoginIdAvailable] = useState<boolean | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [checkingLoginId, setCheckingLoginId] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [verifyingCode, setVerifyingCode] = useState(false);

  if (member) return <Navigate to="/" replace />;

  const updateText = (field: TextField, value: string) => {
    setForm((current) => ({
      ...current,
      [field]: value,
      ...(field === "email" ? { emailVerificationToken: "" } : {}),
    }));
    if (field === "loginId") setLoginIdAvailable(null);
  };
  const updateConsent = (field: ConsentField, checked: boolean) =>
    setForm((current) => ({ ...current, [field]: checked }));

  const checkLoginId = async () => {
    setError(""); setNotice(""); setCheckingLoginId(true);
    try {
      const response = await checkLoginIdAvailability(form.loginId);
      setLoginIdAvailable(response.available);
      if (response.available) setNotice("사용 가능한 아이디입니다.");
      else setError("이미 사용 중인 아이디입니다.");
    } catch (reason) {
      setLoginIdAvailable(null);
      setError(reason instanceof Error ? reason.message : "아이디 중복 확인에 실패했습니다.");
    } finally { setCheckingLoginId(false); }
  };

  const requestCode = async () => {
    setError(""); setNotice(""); setSendingCode(true);
    try {
      await sendEmailVerificationCode(form.email);
      setNotice("인증 코드를 이메일로 보냈습니다. 받은 편지함을 확인해 주세요.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "인증 코드를 보내지 못했습니다.");
    } finally { setSendingCode(false); }
  };

  const confirmCode = async () => {
    setError(""); setNotice(""); setVerifyingCode(true);
    try {
      const response = await confirmEmailVerificationCode(form.email, emailCode);
      setForm((current) => ({ ...current, emailVerificationToken: response.verificationToken }));
      setNotice("이메일 인증이 완료되었습니다.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "인증 코드를 확인하지 못했습니다.");
    } finally { setVerifyingCode(false); }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError(""); setSubmitting(true);
    try { await signup(form); navigate("/"); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "회원가입에 실패했습니다."); }
    finally { setSubmitting(false); }
  };

  const canSignup = Boolean(loginIdAvailable && form.emailVerificationToken && form.termsAgreed && form.privacyCollectionAgreed);

  return <main className="auth-page"><section className="auth-card"><div className="auth-brand"><span className="brand-mark"><span>J</span></span><div><strong>JobPilot AI</strong><small>career action coach</small></div></div><span className="eyebrow">CREATE ACCOUNT</span><h1>회원가입</h1><p>계정을 만든 뒤 목표 직무와 역량 근거를 등록할 수 있습니다.</p><form onSubmit={submit}><label>아이디<div className="field-input-action"><input required minLength={6} maxLength={80} value={form.loginId} onChange={(event) => updateText("loginId", event.target.value)} autoComplete="username" placeholder="영문·숫자 6자 이상" /><button type="button" className="outline-button" onClick={checkLoginId} disabled={checkingLoginId || form.loginId.length < 6}>{checkingLoginId ? "확인 중..." : "중복 확인"}</button></div>{loginIdAvailable && <small className="field-status success">사용 가능한 아이디입니다.</small>}</label><label>이메일<div className="field-input-action"><input required type="email" value={form.email} onChange={(event) => updateText("email", event.target.value)} autoComplete="email" /><button type="button" className="outline-button" onClick={requestCode} disabled={sendingCode}>{sendingCode ? "발송 중..." : "인증 코드 보내기"}</button></div></label><label>이메일 인증 코드<div className="field-input-action"><input required inputMode="numeric" pattern="[0-9]{6}" maxLength={6} value={emailCode} onChange={(event) => setEmailCode(event.target.value.replace(/\D/g, ""))} placeholder="6자리 코드" /><button type="button" className="outline-button" onClick={confirmCode} disabled={verifyingCode || !emailCode}>{verifyingCode ? "확인 중..." : "코드 확인"}</button></div></label>{form.emailVerificationToken && <div className="auth-notice">이메일 인증 완료</div>}<label>닉네임<input required minLength={2} value={form.nickname} onChange={(event) => updateText("nickname", event.target.value)} /></label><label>비밀번호<input required type="password" minLength={8} maxLength={72} value={form.password} onChange={(event) => updateText("password", event.target.value)} autoComplete="new-password" placeholder="8자 이상" /></label><section className="consent-section" aria-labelledby="consent-heading"><strong id="consent-heading">약관 및 수신 동의</strong><label className="consent-check"><input required type="checkbox" checked={form.termsAgreed} onChange={(event) => updateConsent("termsAgreed", event.target.checked)} /><span><b>[필수] 서비스 이용약관 동의</b><small>계정 생성 및 JobPilot AI 서비스 이용에 필요합니다.</small><details><summary>내용 보기</summary><p>서비스 목적에 맞게 계정을 사용하고, 타인의 정보를 무단으로 이용하거나 서비스를 방해하지 않습니다.</p></details></span></label><label className="consent-check"><input required type="checkbox" checked={form.privacyCollectionAgreed} onChange={(event) => updateConsent("privacyCollectionAgreed", event.target.checked)} /><span><b>[필수] 개인정보 수집 및 이용 동의</b><small>계정 생성·인증과 맞춤형 서비스 제공을 위해 필요합니다.</small><details><summary>내용 보기</summary><p>로그인 아이디, 이메일, 닉네임과 비밀번호 해시를 계정 관리 및 서비스 제공 목적으로 처리합니다.</p></details></span></label><label className="consent-check"><input type="checkbox" checked={form.marketingEmailAgreed} onChange={(event) => updateConsent("marketingEmailAgreed", event.target.checked)} /><span><b>[선택] 이메일로 맞춤 추천 정보 수신</b><small>채용공고·교육·지원 전략 등의 추천 정보를 이메일로 받을 수 있습니다. 동의하지 않아도 가입할 수 있습니다.</small></span></label></section>{error && <div className="auth-error">{error}</div>}{notice && <div className="auth-notice">{notice}</div>}<button className="primary-button" disabled={submitting || !canSignup}>{submitting ? "가입 중..." : canSignup ? "회원가입" : "아이디 중복 확인·필수 동의·이메일 인증 후 가입 가능"}</button></form><div className="auth-switch">이미 계정이 있나요? <Link to="/login">로그인</Link></div></section></main>;
}

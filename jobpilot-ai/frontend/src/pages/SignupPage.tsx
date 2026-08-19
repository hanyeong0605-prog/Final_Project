import { FormEvent, useMemo, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { checkLoginIdAvailability, confirmEmailVerificationCode, sendEmailVerificationCode } from "../features/auth/api/authApi";
import { useAuth } from "../features/auth/model/AuthContext";

type TextField = "loginId" | "email" | "password" | "nickname";
type ConsentField = "termsAgreed" | "privacyCollectionAgreed" | "marketingEmailAgreed";

const loginIdPattern = /^[a-zA-Z0-9._-]{6,80}$/;
const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d\s]).{10,72}$/;

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
  const [passwordConfirmation, setPasswordConfirmation] = useState("");
  const [emailCode, setEmailCode] = useState("");
  const [loginIdAvailable, setLoginIdAvailable] = useState<boolean | null>(null);
  const [error, setError] = useState("");
  const [loginIdMessage, setLoginIdMessage] = useState("");
  const [emailMessage, setEmailMessage] = useState("");
  const [codeMessage, setCodeMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [checkingLoginId, setCheckingLoginId] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [verifyingCode, setVerifyingCode] = useState(false);

  const loginIdValid = loginIdPattern.test(form.loginId);
  const passwordValid = passwordPattern.test(form.password);
  const passwordsMatch = Boolean(form.password) && form.password === passwordConfirmation;
  const canSignup = Boolean(
    loginIdAvailable
      && form.emailVerificationToken
      && form.termsAgreed
      && form.privacyCollectionAgreed
      && passwordValid
      && passwordsMatch,
  );

  const signupBlockReason = useMemo(() => {
    if (!loginIdAvailable) return "아이디 중복 확인을 완료해 주세요.";
    if (!form.emailVerificationToken) return "이메일 인증을 완료해 주세요.";
    if (!passwordValid) return "비밀번호 조건을 확인해 주세요.";
    if (!passwordsMatch) return "비밀번호 확인이 일치하지 않습니다.";
    if (!form.termsAgreed || !form.privacyCollectionAgreed) return "필수 약관에 동의해 주세요.";
    return "";
  }, [form.emailVerificationToken, form.privacyCollectionAgreed, form.termsAgreed, loginIdAvailable, passwordValid, passwordsMatch]);

  if (member) return <Navigate to="/" replace />;

  const updateText = (field: TextField, value: string) => {
    setForm((current) => ({
      ...current,
      [field]: value,
      ...(field === "email" ? { emailVerificationToken: "" } : {}),
    }));
    if (field === "loginId") {
      setLoginIdAvailable(null);
      setLoginIdMessage("");
    }
    if (field === "email") {
      setEmailMessage("");
      setCodeMessage("");
    }
  };
  const updateConsent = (field: ConsentField, checked: boolean) =>
    setForm((current) => ({ ...current, [field]: checked }));

  const checkLoginId = async () => {
    setError("");
    setLoginIdMessage("");
    if (!loginIdValid) {
      setLoginIdAvailable(null);
      setLoginIdMessage("아이디는 영문·숫자·점·밑줄·하이픈만 사용해 6~80자로 입력해 주세요. 이메일 주소는 사용할 수 없습니다.");
      return;
    }
    setCheckingLoginId(true);
    try {
      const response = await checkLoginIdAvailability(form.loginId);
      setLoginIdAvailable(response.available);
      setLoginIdMessage(response.available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다.");
    } catch (reason) {
      setLoginIdAvailable(null);
      setLoginIdMessage(reason instanceof Error ? reason.message : "아이디 중복 확인에 실패했습니다.");
    } finally {
      setCheckingLoginId(false);
    }
  };

  const requestCode = async () => {
    setError("");
    setEmailMessage("");
    setCodeMessage("");
    setSendingCode(true);
    try {
      await sendEmailVerificationCode(form.email);
      setEmailMessage("인증 코드를 이메일로 보냈습니다. 받은 편지함과 스팸함을 확인해 주세요.");
    } catch (reason) {
      setEmailMessage(reason instanceof Error ? reason.message : "인증 코드를 보내지 못했습니다.");
    } finally {
      setSendingCode(false);
    }
  };

  const confirmCode = async () => {
    setError("");
    setCodeMessage("");
    setVerifyingCode(true);
    try {
      const response = await confirmEmailVerificationCode(form.email, emailCode);
      setForm((current) => ({ ...current, emailVerificationToken: response.verificationToken }));
      setCodeMessage("이메일 인증이 완료되었습니다.");
    } catch (reason) {
      setCodeMessage(reason instanceof Error ? reason.message : "인증 코드를 확인하지 못했습니다.");
    } finally {
      setVerifyingCode(false);
    }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!canSignup) {
      setError(signupBlockReason);
      return;
    }
    setError("");
    setSubmitting(true);
    try {
      await signup(form);
      navigate("/");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "회원가입에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return <main className="auth-page"><section className="auth-card"><div className="auth-brand"><span className="brand-mark"><span>J</span></span><div><strong>Job-A-Dream AI</strong><small>career action coach</small></div></div><span className="eyebrow">CREATE ACCOUNT</span><h1>회원가입</h1><p>계정을 만든 뒤 목표 직무와 역량 근거를 등록할 수 있습니다.</p><form onSubmit={submit}>
    <label>아이디<div className="field-input-action"><input required minLength={6} maxLength={80} value={form.loginId} onChange={(event) => updateText("loginId", event.target.value)} autoComplete="username" placeholder="영문·숫자·._- 6자 이상" /><button type="button" className="outline-button" onClick={checkLoginId} disabled={checkingLoginId || !form.loginId}>{checkingLoginId ? "확인 중..." : "중복 확인"}</button></div><small className={`field-status ${loginIdAvailable ? "success" : loginIdMessage ? "error" : "hint"}`}>{loginIdMessage || "이메일 주소 대신 로그인에 사용할 아이디를 입력해 주세요."}</small></label>
    <label>이메일<div className="field-input-action"><input required type="email" value={form.email} onChange={(event) => updateText("email", event.target.value)} autoComplete="email" /><button type="button" className="outline-button" onClick={requestCode} disabled={sendingCode || !form.email}>{sendingCode ? "발송 중..." : "인증 코드 보내기"}</button></div>{emailMessage && <small className={`field-status ${emailMessage.includes("보냈습니다") ? "success" : "error"}`}>{emailMessage}</small>}</label>
    <label>이메일 인증 코드<div className="field-input-action"><input required inputMode="numeric" pattern="[0-9]{6}" maxLength={6} value={emailCode} onChange={(event) => setEmailCode(event.target.value.replace(/\D/g, ""))} placeholder="6자리 코드" /><button type="button" className="outline-button" onClick={confirmCode} disabled={verifyingCode || emailCode.length !== 6}>{verifyingCode ? "확인 중..." : "코드 확인"}</button></div>{codeMessage && <small className={`field-status ${form.emailVerificationToken ? "success" : "error"}`}>{codeMessage}</small>}</label>
    <label>닉네임<input required minLength={2} value={form.nickname} onChange={(event) => updateText("nickname", event.target.value)} /></label>
    <label>비밀번호<input required type="password" minLength={10} maxLength={72} value={form.password} onChange={(event) => updateText("password", event.target.value)} autoComplete="new-password" placeholder="영문·숫자·특수문자 포함 10자 이상" /><small className={`field-status ${form.password ? (passwordValid ? "success" : "error") : "hint"}`}>{form.password ? (passwordValid ? "사용 가능한 비밀번호입니다." : "영문·숫자·특수문자를 모두 포함해 10자 이상 입력해 주세요.") : "영문·숫자·특수문자를 모두 포함해 10자 이상 입력해 주세요."}</small></label>
    <label>비밀번호 확인<input required type="password" minLength={10} maxLength={72} value={passwordConfirmation} onChange={(event) => setPasswordConfirmation(event.target.value)} autoComplete="new-password" placeholder="비밀번호를 한 번 더 입력해 주세요" /><small className={`field-status ${passwordConfirmation ? (passwordsMatch ? "success" : "error") : "hint"}`}>{passwordConfirmation ? (passwordsMatch ? "비밀번호가 일치합니다." : "비밀번호가 일치하지 않습니다.") : ""}</small></label>
    <section className="consent-section" aria-labelledby="consent-heading"><strong id="consent-heading">약관 및 수신 동의</strong><label className="consent-check"><input required type="checkbox" checked={form.termsAgreed} onChange={(event) => updateConsent("termsAgreed", event.target.checked)} /><span><b>[필수] 서비스 이용약관 동의</b><small>계정 생성 및 Job-A-Dream AI 서비스 이용에 필요합니다.</small><details><summary>내용 보기</summary><p>서비스 목적에 맞게 계정을 사용하고, 타인의 정보를 무단으로 이용하거나 서비스를 방해하지 않습니다.</p></details></span></label><label className="consent-check"><input required type="checkbox" checked={form.privacyCollectionAgreed} onChange={(event) => updateConsent("privacyCollectionAgreed", event.target.checked)} /><span><b>[필수] 개인정보 수집 및 이용 동의</b><small>계정 생성·인증과 맞춤형 서비스 제공을 위해 필요합니다.</small><details><summary>내용 보기</summary><p>로그인 아이디, 이메일, 닉네임과 비밀번호 해시를 계정 관리 및 서비스 제공 목적으로 처리합니다.</p></details></span></label><label className="consent-check"><input type="checkbox" checked={form.marketingEmailAgreed} onChange={(event) => updateConsent("marketingEmailAgreed", event.target.checked)} /><span><b>[선택] 이메일로 맞춤 추천 정보 수신</b><small>채용공고·교육·지원 전략 등의 추천 정보를 이메일로 받을 수 있습니다. 동의하지 않아도 가입할 수 있습니다.</small></span></label></section>
    {error && <div className="auth-error">{error}</div>}<button className="primary-button" disabled={submitting || !canSignup}>{submitting ? "가입 중..." : canSignup ? "회원가입 완료" : signupBlockReason}</button></form><div className="auth-switch">이미 계정이 있나요? <Link to="/login">로그인</Link></div><div className="auth-switch">채용 담당자이신가요? <Link to="/employer/signup">기업회원 가입</Link></div></section></main>;
}

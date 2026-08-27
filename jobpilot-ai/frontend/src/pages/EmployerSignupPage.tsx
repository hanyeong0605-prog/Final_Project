import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useEmployerAuth } from "../features/employer/model/EmployerAuthContext";
import { checkEmployerLoginIdAvailability } from "../features/employer/api/employerAuthApi";
import type { EmployerSignupInput } from "../features/employer/model/employer.types";
import { AccountTypeToggle } from "../shared/components/AccountTypeToggle";
import { PostcodeSearchModal } from "../features/location-jobs/components/PostcodeSearchModal";
import { BusinessNumberKeypadModal } from "../features/employer/components/BusinessNumberKeypadModal";

const initialForm: EmployerSignupInput = {
  loginId: "", email: "", password: "", managerName: "", managerPhone: "",
  companyName: "", businessRegistrationNumber: "", representativeName: "", openingDate: "", companyAddress: "",
};

function formatBusinessNumber(digits: string): string {
  return [digits.slice(0, 3), digits.slice(3, 5), digits.slice(5, 10)].filter(Boolean).join("-");
}

export function EmployerSignupPage() {
  const { employer, signup } = useEmployerAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState<EmployerSignupInput>(initialForm);
  const [passwordConfirmation, setPasswordConfirmation] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [addressSearchOpen, setAddressSearchOpen] = useState(false);
  const [businessNumberModalOpen, setBusinessNumberModalOpen] = useState(false);
  const [baseAddress, setBaseAddress] = useState("");
  const [detailAddress, setDetailAddress] = useState("");
  // 2026-08-26: 개인회원 가입(SignupPage.tsx)과 같은 아이디 중복확인 패턴 - loginId를 고칠
  // 때마다 다시 확인하게 null로 리셋한다.
  const [loginIdAvailable, setLoginIdAvailable] = useState<boolean | null>(null);
  const [loginIdMessage, setLoginIdMessage] = useState("");
  const [checkingLoginId, setCheckingLoginId] = useState(false);

  if (employer) return <Navigate to="/employer" replace />;

  const passwordsMatch = Boolean(form.password) && form.password === passwordConfirmation;

  const update = (field: keyof EmployerSignupInput) => (event: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }));
    if (field === "loginId") {
      setLoginIdAvailable(null);
      setLoginIdMessage("");
    }
  };

  const updateDigits = (field: "businessRegistrationNumber" | "openingDate", maxLength: number) =>
    (event: React.ChangeEvent<HTMLInputElement>) => {
      const value = event.target.value.replace(/\D/g, "").slice(0, maxLength);
      setForm((prev) => ({ ...prev, [field]: value }));
    };

  const checkLoginId = async () => {
    setError("");
    setLoginIdMessage("");
    if (!form.loginId.trim()) return;
    setCheckingLoginId(true);
    try {
      const response = await checkEmployerLoginIdAvailability(form.loginId);
      setLoginIdAvailable(response.available);
      setLoginIdMessage(response.available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다.");
    } catch (reason) {
      setLoginIdAvailable(null);
      setLoginIdMessage(reason instanceof Error ? reason.message : "아이디 중복 확인에 실패했습니다.");
    } finally {
      setCheckingLoginId(false);
    }
  };

  const applyAddress = (base: string, detail: string) => {
    setForm((prev) => ({ ...prev, companyAddress: [base, detail].filter(Boolean).join(" ") }));
  };

  const handleSelectAddress = (address: string) => {
    setBaseAddress(address);
    applyAddress(address, detailAddress);
  };

  const handleDetailAddressChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setDetailAddress(event.target.value);
    applyAddress(baseAddress, event.target.value);
  };

  const blockReason = !loginIdAvailable
    ? "아이디 중복 확인을 완료해 주세요."
    : !passwordsMatch
      ? "비밀번호 확인이 일치하지 않습니다."
      : "";

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (blockReason) {
      setError(blockReason);
      return;
    }
    setError("");
    setSubmitting(true);
    try {
      await signup(form);
      navigate("/employer/login?signup=pending");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "가입에 실패했습니다.");
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
        <AccountTypeToggle value="employer" memberTo="/signup" employerTo="/employer/signup" />
        <span className="eyebrow">EMPLOYER SIGNUP</span>
        <h1>기업회원 가입</h1>
        <p>
          사업자등록번호는 가입 즉시 국세청 진위확인 API로 자동 확인되고, 관리자 최종 승인 후
          채용공고를 등록할 수 있어요.
        </p>
        <form onSubmit={submit}>
          <label>
            로그인 아이디
            <div className="field-input-action">
              <input required value={form.loginId} onChange={update("loginId")} autoComplete="username" />
              <button type="button" className="outline-button" onClick={() => void checkLoginId()} disabled={checkingLoginId || !form.loginId}>
                {checkingLoginId ? "확인 중..." : "중복 확인"}
              </button>
            </div>
            {loginIdMessage && <small className={`field-status ${loginIdAvailable ? "success" : "error"}`}>{loginIdMessage}</small>}
          </label>
          <label>이메일<input required type="email" value={form.email} onChange={update("email")} autoComplete="email" /></label>
          <label>비밀번호<input required type="password" value={form.password} onChange={update("password")} autoComplete="new-password" placeholder="영문+숫자+특수문자 10자 이상" /></label>
          <label>
            비밀번호 확인
            <input required type="password" value={passwordConfirmation} onChange={(event) => setPasswordConfirmation(event.target.value)} autoComplete="new-password" placeholder="비밀번호를 한 번 더 입력해 주세요" />
            {passwordConfirmation && <small className={`field-status ${passwordsMatch ? "success" : "error"}`}>{passwordsMatch ? "비밀번호가 일치합니다." : "비밀번호가 일치하지 않습니다."}</small>}
          </label>
          <label>담당자 이름<input required value={form.managerName} onChange={update("managerName")} /></label>
          <label>담당자 연락처<input value={form.managerPhone} onChange={update("managerPhone")} placeholder="선택 입력" /></label>
          <label>회사명<input required value={form.companyName} onChange={update("companyName")} /></label>
          <label>
            사업자등록번호
            <div className="field-input-action">
              <input
                required
                readOnly
                inputMode="numeric"
                value={form.businessRegistrationNumber ? formatBusinessNumber(form.businessRegistrationNumber) : ""}
                onClick={() => setBusinessNumberModalOpen(true)}
                placeholder="키패드로 입력해 주세요"
              />
              <button type="button" className="outline-button" onClick={() => setBusinessNumberModalOpen(true)}>키패드로 입력</button>
            </div>
          </label>
          <label>대표자명<input required value={form.representativeName} onChange={update("representativeName")} /></label>
          <label>개업일자<input required inputMode="numeric" pattern="[0-9]{8}" minLength={8} maxLength={8} value={form.openingDate} onChange={updateDigits("openingDate", 8)} placeholder="YYYYMMDD" title="개업일자를 YYYYMMDD 8자리로 입력해 주세요." /></label>
          <label>
            회사 주소
            <div className="field-input-action">
              <input value={baseAddress} readOnly placeholder="주소 찾기를 눌러 주세요 (선택 입력)" />
              <button type="button" className="outline-button" onClick={() => setAddressSearchOpen(true)}>주소 찾기</button>
            </div>
            {baseAddress && <input value={detailAddress} onChange={handleDetailAddressChange} placeholder="상세 주소 (동/호수 등, 선택 입력)" />}
          </label>
          {error && <div className="auth-error">{error}</div>}
          <button className="primary-button" disabled={submitting || Boolean(blockReason)}>
            {submitting ? "가입 처리 중..." : blockReason || "기업회원 가입"}
          </button>
        </form>

        <div className="auth-switch">이미 가입하셨나요? <Link to="/employer/login">기업회원 로그인</Link></div>
      </section>

      <PostcodeSearchModal
        isOpen={addressSearchOpen}
        onClose={() => setAddressSearchOpen(false)}
        onSelectAddress={handleSelectAddress}
      />
      <BusinessNumberKeypadModal
        isOpen={businessNumberModalOpen}
        initialValue={form.businessRegistrationNumber}
        onClose={() => setBusinessNumberModalOpen(false)}
        onConfirm={(digits) => setForm((prev) => ({ ...prev, businessRegistrationNumber: digits }))}
      />
    </main>
  );
}

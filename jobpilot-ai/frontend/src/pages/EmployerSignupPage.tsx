import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useEmployerAuth } from "../features/employer/model/EmployerAuthContext";
import type { EmployerSignupInput } from "../features/employer/model/employer.types";
import { AccountTypeToggle } from "../shared/components/AccountTypeToggle";
import { PostcodeSearchModal } from "../features/location-jobs/components/PostcodeSearchModal";

const initialForm: EmployerSignupInput = {
  loginId: "", email: "", password: "", managerName: "", managerPhone: "",
  companyName: "", businessRegistrationNumber: "", representativeName: "", openingDate: "", companyAddress: "",
};

export function EmployerSignupPage() {
  const { employer, signup } = useEmployerAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState<EmployerSignupInput>(initialForm);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [addressSearchOpen, setAddressSearchOpen] = useState(false);
  const [baseAddress, setBaseAddress] = useState("");
  const [detailAddress, setDetailAddress] = useState("");

  if (employer) return <Navigate to="/employer" replace />;

  const update = (field: keyof EmployerSignupInput) => (event: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }));
  };

  const updateDigits = (field: "businessRegistrationNumber" | "openingDate", maxLength: number) =>
    (event: React.ChangeEvent<HTMLInputElement>) => {
      const value = event.target.value.replace(/\D/g, "").slice(0, maxLength);
      setForm((prev) => ({ ...prev, [field]: value }));
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

  const submit = async (event: FormEvent) => {
    event.preventDefault();
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
          <label>로그인 아이디<input required value={form.loginId} onChange={update("loginId")} autoComplete="username" /></label>
          <label>이메일<input required type="email" value={form.email} onChange={update("email")} autoComplete="email" /></label>
          <label>비밀번호<input required type="password" value={form.password} onChange={update("password")} autoComplete="new-password" placeholder="영문+숫자+특수문자 10자 이상" /></label>
          <label>담당자 이름<input required value={form.managerName} onChange={update("managerName")} /></label>
          <label>담당자 연락처<input value={form.managerPhone} onChange={update("managerPhone")} placeholder="선택 입력" /></label>
          <label>회사명<input required value={form.companyName} onChange={update("companyName")} /></label>
          <label>사업자등록번호<input required inputMode="numeric" pattern="[0-9]{10}" minLength={10} maxLength={10} value={form.businessRegistrationNumber} onChange={updateDigits("businessRegistrationNumber", 10)} placeholder="숫자 10자리" title="사업자등록번호 숫자 10자리를 입력해 주세요." /></label>
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
          <button className="primary-button" disabled={submitting}>{submitting ? "가입 처리 중..." : "기업회원 가입"}</button>
        </form>

        <div className="auth-switch">이미 가입하셨나요? <Link to="/employer/login">기업회원 로그인</Link></div>
      </section>

      <PostcodeSearchModal
        isOpen={addressSearchOpen}
        onClose={() => setAddressSearchOpen(false)}
        onSelectAddress={handleSelectAddress}
      />
    </main>
  );
}

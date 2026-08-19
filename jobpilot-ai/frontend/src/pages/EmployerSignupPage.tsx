import { FormEvent, useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useEmployerAuth } from "../features/employer/model/EmployerAuthContext";
import type { EmployerSignupInput } from "../features/employer/model/employer.types";

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

  if (employer) return <Navigate to="/employer" replace />;

  const update = (field: keyof EmployerSignupInput) => (event: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }));
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      await signup(form);
      navigate("/employer");
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
          <label>사업자등록번호<input required value={form.businessRegistrationNumber} onChange={update("businessRegistrationNumber")} placeholder="1234567890 (하이픈 없이)" /></label>
          <label>대표자명<input required value={form.representativeName} onChange={update("representativeName")} /></label>
          <label>개업일자<input required value={form.openingDate} onChange={update("openingDate")} placeholder="YYYYMMDD" maxLength={8} /></label>
          <label>회사 주소<input value={form.companyAddress} onChange={update("companyAddress")} placeholder="선택 입력" /></label>
          {error && <div className="auth-error">{error}</div>}
          <button className="primary-button" disabled={submitting}>{submitting ? "가입 처리 중..." : "기업회원 가입"}</button>
        </form>

        <div className="auth-switch">이미 가입하셨나요? <Link to="/employer/login">기업회원 로그인</Link></div>
      </section>
    </main>
  );
}

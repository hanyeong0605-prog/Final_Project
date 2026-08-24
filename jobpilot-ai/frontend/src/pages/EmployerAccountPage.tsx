import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { updateMe, withdraw } from "../features/employer/api/employerAuthApi";
import { PostcodeSearchModal } from "../features/location-jobs/components/PostcodeSearchModal";
import { useEmployerAuth } from "../features/employer/model/EmployerAuthContext";
import type { EmployerProfileInput } from "../features/employer/model/employer.types";
import { PageHeading } from "../shared/components/PageHeading";

export function EmployerAccountPage() {
  const { employer, logout, setCurrentEmployer } = useEmployerAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState<EmployerProfileInput>(() => ({
    loginId: employer?.loginId ?? "", email: employer?.email ?? "", newPassword: "",
    managerName: employer?.managerName ?? "", managerPhone: employer?.managerPhone ?? "",
    companyName: employer?.companyName ?? "", representativeName: employer?.representativeName ?? "",
    openingDate: employer?.openingDate ?? "", companyAddress: employer?.companyAddress ?? "",
  }));
  const [addressOpen, setAddressOpen] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  if (!employer) return null;

  const update = (field: keyof EmployerProfileInput) => (event: React.ChangeEvent<HTMLInputElement>) =>
    setForm((value) => ({ ...value, [field]: event.target.value }));

  const save = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError(""); setNotice("");
    try { const updated = await updateMe(form); setCurrentEmployer(updated); setNotice("기업 정보를 수정했습니다."); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "기업 정보 수정에 실패했습니다."); }
    finally { setSaving(false); }
  };

  const removeAccount = async () => {
    if (!window.confirm("기업회원에서 탈퇴하시겠습니까? 등록한 공고는 보존되지만 더 이상 관리할 수 없습니다.")) return;
    try { await withdraw(); logout(); navigate("/employer/login", { replace: true }); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "회원 탈퇴에 실패했습니다."); }
  };

  return <main className="content">
    <PageHeading eyebrow="EMPLOYER ACCOUNT" title="기업 마이페이지" body="사업자등록번호를 제외한 기업회원 정보를 수정할 수 있습니다." />
    {(notice || error) && <div className={error ? "account-alert error" : "account-alert"}>{error || notice}</div>}
    <section className="panel admin-panel"><form className="employer-posting-form" onSubmit={save}>
      <label>로그인 아이디<input required value={form.loginId} onChange={update("loginId")} /></label>
      <label>이메일<input required type="email" value={form.email} onChange={update("email")} /></label>
      {employer.passwordlessStatus !== "ACTIVE" && <label>새 비밀번호<input type="password" value={form.newPassword ?? ""} onChange={update("newPassword")} placeholder="변경할 때만 입력" /></label>}
      <label>담당자 이름<input required value={form.managerName} onChange={update("managerName")} /></label>
      <label>담당자 연락처<input value={form.managerPhone ?? ""} onChange={update("managerPhone")} /></label>
      <label>회사명<input required value={form.companyName} onChange={update("companyName")} /></label>
      <label>사업자등록번호<input value={employer.businessRegistrationNumber} disabled /></label>
      <label>대표자명<input required value={form.representativeName} onChange={update("representativeName")} /></label>
      <label>개업일자<input required inputMode="numeric" pattern="[0-9]{8}" value={form.openingDate} onChange={update("openingDate")} /></label>
      <label>회사 주소<div className="field-input-action"><input value={form.companyAddress ?? ""} readOnly /><button type="button" className="outline-button" onClick={() => setAddressOpen(true)}>주소 찾기</button></div></label>
      <div className="admin-table-actions"><button className="primary-button" disabled={saving}>{saving ? "저장 중..." : "정보 수정"}</button></div>
    </form></section>
    <section className="panel admin-panel employer-withdraw-panel"><div><h2>기업회원 탈퇴</h2><p>탈퇴하면 Passwordless 인증이 해제되고 기업회원 기능을 더 이상 이용할 수 없습니다.</p></div><button className="outline-button danger" onClick={() => void removeAccount()}>회원 탈퇴</button></section>
    <PostcodeSearchModal isOpen={addressOpen} onClose={() => setAddressOpen(false)} onSelectAddress={(address) => setForm((value) => ({ ...value, companyAddress: address }))} />
  </main>;
}

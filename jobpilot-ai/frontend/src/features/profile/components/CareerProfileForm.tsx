import { type ReactNode, useEffect, useState } from "react";
import { jobFamilies } from "../data/profileCatalog";
import { emptyCareerProfile, type CareerProfile } from "../model/careerProfile.types";
import type { MemberSkill } from "../model/memberSkill.types";
import { emptyMemberCertificate, type MemberCertificate } from "../model/memberCertificate.types";
import { CertificateSearchModal } from "./CertificateSearchModal";
import { RegionSelectionModal } from "./RegionSelectionModal";
import { SkillProfileEditor } from "./SkillProfileEditor";

type Props = {
  initial?: CareerProfile;
  initialSkills?: MemberSkill[];
  initialCertificates?: MemberCertificate[];
  onCertificatesChange?: (value: MemberCertificate[]) => void;
  onSave: (value: CareerProfile, skills: MemberSkill[], certificates: MemberCertificate[]) => Promise<void>;
  onCancel?: () => void;
  saveLabel?: string;
  educationSection?: ReactNode;
};

export function CareerProfileForm({ initial, initialSkills, initialCertificates, onCertificatesChange, onSave, educationSection }: Props) {
  const [form, setForm] = useState<CareerProfile>(initial ?? emptyCareerProfile());
  const [skills, setSkills] = useState<MemberSkill[]>(initialSkills ?? []);
  const [certificates, setCertificates] = useState<MemberCertificate[]>(initialCertificates ?? []);
  const [saving, setSaving] = useState(false);
  useEffect(() => { if (initial) setForm(initial); }, [initial]);
  useEffect(() => { if (initialSkills) setSkills(initialSkills); }, [initialSkills]);
  useEffect(() => { if (initialCertificates) setCertificates(initialCertificates); }, [initialCertificates]);
  useEffect(() => { onCertificatesChange?.(certificates); }, [certificates, onCertificatesChange]);
  useEffect(() => { const resetProfile = () => { setForm(emptyCareerProfile()); setSkills([]); setCertificates([]); }; window.addEventListener("resume-profile:reset", resetProfile); return () => window.removeEventListener("resume-profile:reset", resetProfile); }, []);
  useEffect(() => {
    const addCertificate = () => setCertificates((current) => current.length >= 20 ? current : [...current, emptyMemberCertificate()]);
    const removeCertificate = () => setCertificates((current) => {
      const certificate = current.at(-1);
      const hasContent = certificate && Object.values(certificate).some((value) => typeof value === "string" && value.trim());
      return hasContent && !confirm("작성중이던 내용이 있습니다. 정말로 삭제하시겠습니까?") ? current : current.slice(0, -1);
    });
    window.addEventListener("resume-certificates:add", addCertificate);
    window.addEventListener("resume-certificates:remove", removeCertificate);
    return () => { window.removeEventListener("resume-certificates:add", addCertificate); window.removeEventListener("resume-certificates:remove", removeCertificate); };
  }, []);
  const notify = (type: "success" | "error", text: string) => window.dispatchEvent(new CustomEvent("resume:toast", { detail: { type, text } }));
  const focusSection = (id: string) => window.setTimeout(() => { const section = document.getElementById(id); section?.scrollIntoView({ behavior: "smooth", block: "center" }); (section?.querySelector("input, select, textarea") as HTMLElement | null)?.focus({ preventScroll: true }); }, 0);
  const errorMessage = (error: unknown, fallback: string) => error instanceof Error && error.message.trim() ? error.message.trim() : fallback;
  const validate = () => {
    if (!form.targetJobFamily.trim()) { notify("error", "직무 분야는 필수입니다."); focusSection("resume-desired-role"); return false; }
    if (!form.targetRole.trim()) { notify("error", "목표 직무는 필수입니다."); focusSection("resume-desired-role"); return false; }
    const blankCertificate = certificates.findIndex((certificate) => !certificate.name.trim());
    if (blankCertificate >= 0) { notify("error", `자격증 ${blankCertificate + 1}의 자격증명은 필수입니다.`); focusSection("resume-certificates"); return false; }
    return true;
  };
  const set = <K extends keyof CareerProfile>(key: K, value: CareerProfile[K]) => setForm((current) => ({ ...current, [key]: value }));
  const knownFamily = Object.hasOwn(jobFamilies, form.targetJobFamily);
  const roles = knownFamily ? jobFamilies[form.targetJobFamily] : [];
  const knownRole = roles.includes(form.targetRole);
  const saveProfile = async (showSuccess: boolean) => {
    if (!validate()) return false;
    setSaving(true);
    try {
      const validCertificates = certificates.filter((certificate) => certificate.name.trim());
      if (validCertificates.length !== certificates.length) return false;
      await onSave(form, skills, validCertificates);
      if (showSuccess) notify("success", "스펙정보와 보유 기술을 저장했습니다.");
      return true;
    }
    catch (error) {
      const message = errorMessage(error, "서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      if (message.includes("기술") || message.includes("skill")) focusSection("resume-skills");
      else if (message.includes("자격증")) focusSection("resume-certificates");
      else if (message.includes("직무")) focusSection("resume-desired-role");
      notify("error", `저장하지 못했습니다: ${message}`);
      return false;
    }
    finally { setSaving(false); }
  };
  useEffect(() => { const submitProfile = (event: Event) => { event.preventDefault(); const detail = (event as CustomEvent<{ save?: Promise<boolean> }>).detail; const task = saveProfile(false); if (detail) detail.save = task; else void task; }; window.addEventListener("resume-profile:save", submitProfile); return () => window.removeEventListener("resume-profile:save", submitProfile); });
  const changePhoto = (file: File | undefined) => {
    if (!file) return;
    if (!/^image\/(jpeg|png|webp)$/.test(file.type) || file.size > 2 * 1024 * 1024) { notify("error", "사진은 JPG, PNG, WEBP 형식의 2MB 이하 파일만 첨부할 수 있습니다."); return; }
    const reader = new FileReader(); reader.onload = () => set("profilePhotoDataUrl", typeof reader.result === "string" ? reader.result : null); reader.readAsDataURL(file);
  };

  return <form className="career-profile-form" noValidate onSubmit={(event) => { event.preventDefault(); void saveProfile(true); }}>
    <div className="form-section" id="resume-profile-photo"><h3>프로필 사진 <span className="optional-label">선택</span></h3><p className="form-hint">사진 없이도 저장·지원할 수 있습니다. JPG·PNG·WEBP, 최대 2MB.</p><div className="profile-photo-control">{form.profilePhotoDataUrl ? <img src={form.profilePhotoDataUrl} alt="첨부한 프로필 사진 미리보기" /> : <span>사진 없음</span>}<div><input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => changePhoto(event.target.files?.[0])} /><button type="button" className="outline-button" disabled={!form.profilePhotoDataUrl} onClick={() => set("profilePhotoDataUrl", null)}>사진 삭제</button></div></div></div>
    <div className="form-section" id="resume-desired-role"><h3>희망 직무</h3><div className="form-fields">
      <label>직무 분야*<select required value={knownFamily ? form.targetJobFamily : "OTHER"} onChange={(event) => { const next = event.target.value; set("targetJobFamily", next === "OTHER" ? "" : next); set("targetRole", ""); }}><option value="" disabled>직무 분야를 선택하세요</option>{Object.keys(jobFamilies).map((family) => <option key={family} value={family}>{family}</option>)}<option value="OTHER">기타(직접 입력)</option></select></label>
      {!knownFamily ? <label>직접 입력 직무 분야*<input required maxLength={80} value={form.targetJobFamily} onChange={(event) => set("targetJobFamily", event.target.value)} placeholder="예: 건설·환경" /></label> : <label>목표 직무*<select required value={knownRole ? form.targetRole : "OTHER"} onChange={(event) => set("targetRole", event.target.value === "OTHER" ? "" : event.target.value)}><option value="" disabled>목표 직무를 선택하세요</option>{roles.map((role) => <option key={role} value={role}>{role}</option>)}<option value="OTHER">기타(직접 입력)</option></select></label>}
      {knownFamily && !knownRole && <label>직접 입력 목표 직무*<input required maxLength={80} value={form.targetRole} onChange={(event) => set("targetRole", event.target.value)} placeholder="예: 게임 서버 개발자" /></label>}
      {!knownFamily && <label>목표 직무*<input required maxLength={80} value={form.targetRole} onChange={(event) => set("targetRole", event.target.value)} placeholder="예: 게임 서버 개발자" /></label>}
    </div></div>

    <div className="form-section" id="resume-conditions"><h3>지원 조건</h3><div className="form-fields">
      <label>희망 지역<RegionSelectionModal value={form.preferredLocations} onChange={(next) => set("preferredLocations", next)} /></label>
      <label>입사 가능일<input type="date" value={form.availableFrom ?? ""} onChange={(event) => set("availableFrom", event.target.value || null)} /></label>
      <label>경력 구분<select value={form.experienceType} onChange={(event) => { const experienceType = event.target.value; set("experienceType", experienceType); if (experienceType === "ENTRY") set("totalCareerMonths", 0); }}><option value="ENTRY">신입</option><option value="EXPERIENCED">경력</option><option value="ANY">무관</option></select></label>
      <label className={form.experienceType === "ENTRY" ? "is-disabled" : ""}>관련 경력 기간(개월)<input type="number" min={0} disabled={form.experienceType === "ENTRY"} value={form.experienceType === "ENTRY" ? 0 : form.totalCareerMonths} onChange={(event) => set("totalCareerMonths", Number(event.target.value))} /></label>
    </div></div>{educationSection}

    <div className="form-section" id="resume-skills"><SkillProfileEditor value={skills} onChange={setSkills} /></div>

    <div className="form-section" id="resume-certificates"><h3>자격증</h3><p className="form-hint">보유한 자격증만 필요한 수만큼 추가해 입력하세요.</p>
      <CertificateSearchModal showDetail={false} onManual={(name) => setCertificates((current) => [...current, { ...emptyMemberCertificate(), name }])} onSelect={(item) => setCertificates((current) => current.some((certificate) => certificate.name === item.name) ? current : [...current, { ...emptyMemberCertificate(), name: item.name, issuer: "한국산업인력공단" }])} />
      <div className="certificate-list">
        {certificates.map((certificate, index) => <div className="certificate-card" key={certificate.id ?? `new-${index}`}>
          <button type="button" className="certificate-remove" aria-label={`${certificate.name || "자격증"} 삭제`} title="자격증 삭제" onClick={() => setCertificates((current) => { const hasContent = Object.values(certificate).some((value) => typeof value === "string" && value.trim()); return hasContent && !confirm("작성중이던 내용이 있습니다. 정말로 삭제하시겠습니까?") ? current : current.filter((_, itemIndex) => itemIndex !== index); })}>×</button>
          <div className="form-fields">
            <label>자격증명*<input required maxLength={255} value={certificate.name} onChange={(event) => setCertificates((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, name: event.target.value } : item))} placeholder="예: 정보처리기사" /></label>
            <label>발급기관<input maxLength={255} value={certificate.issuer ?? ""} onChange={(event) => setCertificates((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, issuer: event.target.value || null } : item))} placeholder="예: 한국산업인력공단" /></label>
            <label>취득일<input type="date" value={certificate.acquiredAt ?? ""} onChange={(event) => setCertificates((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, acquiredAt: event.target.value || null } : item))} /></label>
          </div>
        </div>)}
      </div>
      <button type="button" className="outline-button certificate-manual-add" disabled={certificates.length >= 20} onClick={() => setCertificates((current) => [...current, emptyMemberCertificate()])}>+ 직접 입력</button>
    </div>

  </form>;
}

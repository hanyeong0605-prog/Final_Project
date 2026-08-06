import { FormEvent, useEffect, useState } from "react";
import { jobFamilies } from "../data/profileCatalog";
import { emptyCareerProfile, type CareerProfile } from "../model/careerProfile.types";
import type { MemberSkill } from "../model/memberSkill.types";
import { EducationSearchModal } from "./EducationSearchModal";
import { RegionSelectionModal } from "./RegionSelectionModal";
import { SkillProfileEditor } from "./SkillProfileEditor";

type Props = {
  initial?: CareerProfile;
  initialSkills?: MemberSkill[];
  onSave: (value: CareerProfile, skills: MemberSkill[]) => Promise<void>;
  onCancel?: () => void;
  saveLabel?: string;
};

export function CareerProfileForm({ initial, initialSkills, onSave, onCancel, saveLabel = "정보 저장하기" }: Props) {
  const [form, setForm] = useState<CareerProfile>(initial ?? emptyCareerProfile());
  const [skills, setSkills] = useState<MemberSkill[]>(initialSkills ?? []);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  useEffect(() => { if (initial) setForm(initial); }, [initial]);
  useEffect(() => { if (initialSkills) setSkills(initialSkills); }, [initialSkills]);

  const set = <K extends keyof CareerProfile>(key: K, value: CareerProfile[K]) => setForm((current) => ({ ...current, [key]: value }));
  const knownFamily = Object.hasOwn(jobFamilies, form.targetJobFamily);
  const roles = knownFamily ? jobFamilies[form.targetJobFamily] : [];
  const knownRole = roles.includes(form.targetRole);
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError(""); setSaving(true);
    try { await onSave(form, skills); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "저장에 실패했습니다."); }
    finally { setSaving(false); }
  };

  return <form className="career-profile-form" onSubmit={submit}>
    <div className="form-section"><h3>희망 직무</h3><div className="form-fields">
      <label>직무 분야*<select required value={knownFamily ? form.targetJobFamily : "OTHER"} onChange={(event) => { const next = event.target.value; set("targetJobFamily", next === "OTHER" ? "" : next); set("targetRole", ""); }}><option value="" disabled>직무 분야를 선택하세요</option>{Object.keys(jobFamilies).map((family) => <option key={family} value={family}>{family}</option>)}<option value="OTHER">기타(직접 입력)</option></select></label>
      {!knownFamily ? <label>직접 입력 직무 분야*<input required maxLength={80} value={form.targetJobFamily} onChange={(event) => set("targetJobFamily", event.target.value)} placeholder="예: 건설·환경" /></label> : <label>목표 직무*<select required value={knownRole ? form.targetRole : "OTHER"} onChange={(event) => set("targetRole", event.target.value === "OTHER" ? "" : event.target.value)}><option value="" disabled>목표 직무를 선택하세요</option>{roles.map((role) => <option key={role} value={role}>{role}</option>)}<option value="OTHER">기타(직접 입력)</option></select></label>}
      {knownFamily && !knownRole && <label>직접 입력 목표 직무*<input required maxLength={80} value={form.targetRole} onChange={(event) => set("targetRole", event.target.value)} placeholder="예: 게임 서버 개발자" /></label>}
      {!knownFamily && <label>목표 직무*<input required maxLength={80} value={form.targetRole} onChange={(event) => set("targetRole", event.target.value)} placeholder="예: 게임 서버 개발자" /></label>}
    </div></div>

    <div className="form-section"><h3>지원 조건</h3><div className="form-fields">
      <label>희망 지역<RegionSelectionModal value={form.preferredLocations} onChange={(next) => set("preferredLocations", next)} /></label>
      <label>입사 가능일<input type="date" value={form.availableFrom ?? ""} onChange={(event) => set("availableFrom", event.target.value || null)} /></label>
      <label>경력 구분<select value={form.experienceType} onChange={(event) => set("experienceType", event.target.value)}><option value="ENTRY">신입</option><option value="EXPERIENCED">경력</option><option value="ANY">무관</option></select></label>
      <label>관련 경력 기간(개월)<input type="number" min={0} value={form.totalCareerMonths} onChange={(event) => set("totalCareerMonths", Number(event.target.value))} /></label>
    </div></div>

    <div className="form-section"><SkillProfileEditor value={skills} onChange={setSkills} /></div>

    <div className="form-section"><h3>학력</h3><div className="form-fields">
      <label>최종 학력<select value={form.educationLevel ?? ""} onChange={(event) => { const next = event.target.value || null; set("educationLevel", next); set("schoolName", null); set("major", null); }}><option value="">선택 안 함</option><option value="HIGH_SCHOOL">고등학교</option><option value="COLLEGE">전문대</option><option value="BACHELOR">대학교</option><option value="MASTER">대학원</option></select></label>
      <label>학교명<EducationSearchModal kind="school" educationLevel={form.educationLevel} selectedSchool={form.schoolName} value={form.schoolName} onSelect={(name) => { set("schoolName", name); set("major", null); }} /></label>
      <label>전공<EducationSearchModal kind="major" educationLevel={form.educationLevel} selectedSchool={form.schoolName} value={form.major} onSelect={(name) => set("major", name)} /></label>
      <label>졸업 상태<select value={form.graduationStatus ?? ""} onChange={(event) => set("graduationStatus", event.target.value || null)}><option value="">선택 안 함</option><option value="GRADUATED">졸업</option><option value="EXPECTED">졸업 예정</option><option value="ENROLLED">재학</option></select></label>
    </div></div>

    <div className="form-section"><h3>외부 근거 및 추가 설명</h3><div className="form-fields">
      <label>GitHub 아이디<input value={form.githubUsername ?? ""} onChange={(event) => set("githubUsername", event.target.value || null)} placeholder="github 사용자명" /></label>
      <label>포트폴리오 URL<input type="url" value={form.portfolioUrl ?? ""} onChange={(event) => set("portfolioUrl", event.target.value || null)} placeholder="https://..." /></label>
      <label className="wide">추가 설명<textarea rows={3} value={form.technicalSummary ?? ""} onChange={(event) => set("technicalSummary", event.target.value || null)} placeholder="프로젝트 역할, 자격증, 교육 이수 등 분석에 참고할 내용을 적어주세요." /></label>
    </div></div>
    {error && <div className="auth-error">{error}</div>}
    <div className="form-actions">{onCancel && <button type="button" className="outline-button" onClick={onCancel}>취소</button>}<button className="primary-button" disabled={saving}>{saving ? "저장 중..." : saveLabel}</button></div>
  </form>;
}

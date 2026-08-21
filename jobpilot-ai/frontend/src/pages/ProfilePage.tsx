import { useEffect, useState } from "react";
import { getCareerProfile, resetCareerProfile, saveCareerProfile } from "../features/profile/api/careerProfileApi";
import { getMemberSkills, saveMemberSkills } from "../features/profile/api/memberSkillsApi";
import { getMemberCertificates, saveMemberCertificates } from "../features/profile/api/memberCertificatesApi";
import { CareerProfileForm } from "../features/profile/components/CareerProfileForm";
import type { CareerProfile } from "../features/profile/model/careerProfile.types";
import type { MemberSkill } from "../features/profile/model/memberSkill.types";
import type { MemberCertificate } from "../features/profile/model/memberCertificate.types";
import { PageHeading } from "../shared/components/PageHeading";
import { ResumeEntryEditor } from "../features/resume/components/ResumeEntryEditor";
import { SelfIntroductionSection } from "../features/resume/components/SelfIntroductionSection";
import { saveResumeSaveState } from "../features/resume/api/resumeSaveStateApi";

export function ProfilePage() {
  const [profile, setProfile] = useState<CareerProfile>();
  const [skills, setSkills] = useState<MemberSkill[]>([]);
  const [certificates, setCertificates] = useState<MemberCertificate[]>([]);
  const [certificateCount, setCertificateCount] = useState(0);

  useEffect(() => {
    void Promise.all([getCareerProfile(), getMemberSkills(), getMemberCertificates()])
      .then(([savedProfile, savedSkills, savedCertificates]) => { setProfile(savedProfile); setSkills(savedSkills); setCertificates(savedCertificates); setCertificateCount(savedCertificates.length); })
      .catch(() => { setProfile(undefined); setSkills([]); setCertificates([]); setCertificateCount(0); });
  }, []);

  const saveError = (area: string, error: unknown): never => { const reason = error instanceof Error && error.message ? error.message : "알 수 없는 오류"; throw new Error(`${area}: ${reason}`); };
  return <>
    <PageHeading eyebrow="EVIDENCE-BASED PROFILE" title="나의 스펙정보" body="입력한 목표·경력·기술 경험을 채용공고의 필수·우대 조건과 비교하는 추천 근거로 사용합니다." />
    <ResumeEntryEditor certificateCount={certificateCount} onSaveState={saveResumeSaveState} onResetAll={async () => { await resetCareerProfile(); setProfile(undefined); setSkills([]); setCertificates([]); setCertificateCount(0); }} onCertificateAction={(action) => window.dispatchEvent(new Event(`resume-certificates:${action}`))} profileEditor={(educationSection) => <section className="panel profile-form-panel">
      <CareerProfileForm educationSection={educationSection} initial={profile} initialSkills={skills} initialCertificates={certificates} onCertificatesChange={(items) => setCertificateCount(items.length)} onSave={async (value, memberSkills, memberCertificates) => {
        const savedProfile = await saveCareerProfile(value).catch((error: unknown) => saveError("스펙정보", error));
        const savedSkills = await saveMemberSkills(memberSkills.map(({ skillId, selfReportedLevel, note }) => ({ skillId, selfReportedLevel, note }))).catch((error: unknown) => saveError("보유 기술", error));
        const savedCertificates = await saveMemberCertificates(memberCertificates.map(({ name, issuer, acquiredAt, expiresAt, officialUrl }) => ({ name, issuer, acquiredAt, expiresAt, officialUrl }))).catch((error: unknown) => saveError("자격증", error));
        setProfile(savedProfile); setSkills(savedSkills); setCertificates(savedCertificates); setCertificateCount(savedCertificates.length);
      }} />
    </section>} selfIntroduction={<section className="panel profile-form-panel" id="resume-self-introduction"><SelfIntroductionSection /></section>} />
  </>;
}

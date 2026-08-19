import { useEffect, useState } from "react";
import { getCareerProfile, saveCareerProfile } from "../features/profile/api/careerProfileApi";
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
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    void Promise.all([getCareerProfile(), getMemberSkills(), getMemberCertificates()])
      .then(([savedProfile, savedSkills, savedCertificates]) => { setProfile(savedProfile); setSkills(savedSkills); setCertificates(savedCertificates); setCertificateCount(savedCertificates.length); })
      .catch(() => { setProfile(undefined); setSkills([]); setCertificates([]); setCertificateCount(0); });
  }, []);

  return <>
    <PageHeading eyebrow="EVIDENCE-BASED PROFILE" title="나의 스펙정보" body="입력한 목표·경력·기술 경험을 채용공고의 필수·우대 조건과 비교하는 추천 근거로 사용합니다." />
    {saved && <div className="account-alert">스펙정보와 보유 기술을 저장했습니다.</div>}
    <ResumeEntryEditor certificateCount={certificateCount} onSaveState={saveResumeSaveState} onCertificateAction={(action) => window.dispatchEvent(new Event(`resume-certificates:${action}`))} profileEditor={(educationSection) => <section className="panel profile-form-panel">
      <CareerProfileForm educationSection={educationSection} initial={profile} initialSkills={skills} initialCertificates={certificates} onCertificatesChange={(items) => setCertificateCount(items.length)} onSave={async (value, memberSkills, memberCertificates) => {
        const savedProfile = await saveCareerProfile(value);
        const savedSkills = await saveMemberSkills(memberSkills.map(({ skillId, selfReportedLevel, note }) => ({ skillId, selfReportedLevel, note })));
        const savedCertificates = await saveMemberCertificates(memberCertificates.map(({ name, issuer, acquiredAt, expiresAt, officialUrl }) => ({ name, issuer, acquiredAt, expiresAt, officialUrl })));
        setProfile(savedProfile); setSkills(savedSkills); setCertificates(savedCertificates); setCertificateCount(savedCertificates.length); setSaved(true);
      }} />
    </section>} selfIntroduction={<section className="panel profile-form-panel" id="resume-self-introduction"><SelfIntroductionSection /></section>} />
  </>;
}

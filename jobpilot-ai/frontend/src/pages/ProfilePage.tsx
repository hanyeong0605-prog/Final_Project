import { useEffect, useState } from "react";
import { getCareerProfile, saveCareerProfile } from "../features/profile/api/careerProfileApi";
import { getMemberSkills, saveMemberSkills } from "../features/profile/api/memberSkillsApi";
import { CareerProfileForm } from "../features/profile/components/CareerProfileForm";
import type { CareerProfile } from "../features/profile/model/careerProfile.types";
import type { MemberSkill } from "../features/profile/model/memberSkill.types";
import { PageHeading } from "../shared/components/PageHeading";

export function ProfilePage() {
  const [profile, setProfile] = useState<CareerProfile>();
  const [skills, setSkills] = useState<MemberSkill[]>([]);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    void Promise.all([getCareerProfile(), getMemberSkills()])
      .then(([savedProfile, savedSkills]) => { setProfile(savedProfile); setSkills(savedSkills); })
      .catch(() => { setProfile(undefined); setSkills([]); });
  }, []);

  return <>
    <PageHeading eyebrow="EVIDENCE-BASED PROFILE" title="나의 스펙정보" body="입력한 목표·경력·기술 경험을 채용공고의 필수·우대 조건과 비교하는 추천 근거로 사용합니다." />
    {saved && <div className="account-alert">스펙정보와 보유 기술을 저장했습니다.</div>}
    <section className="panel profile-form-panel">
      <CareerProfileForm initial={profile} initialSkills={skills} onSave={async (value, memberSkills) => {
        const savedProfile = await saveCareerProfile(value);
        const savedSkills = await saveMemberSkills(memberSkills.map(({ skillId, selfReportedLevel, note }) => ({ skillId, selfReportedLevel, note })));
        setProfile(savedProfile); setSkills(savedSkills); setSaved(true);
      }} />
    </section>
  </>;
}

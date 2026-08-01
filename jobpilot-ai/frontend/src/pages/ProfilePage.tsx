import { useEffect, useState } from "react";
import { getCareerProfile, saveCareerProfile } from "../features/profile/api/careerProfileApi";
import { CareerProfileForm } from "../features/profile/components/CareerProfileForm";
import type { CareerProfile } from "../features/profile/model/careerProfile.types";
import { PageHeading } from "../shared/components/PageHeading";

export function ProfilePage() {
  const [profile, setProfile] = useState<CareerProfile>(); const [saved, setSaved] = useState(false);
  useEffect(() => { void getCareerProfile().then(setProfile).catch(() => setProfile(undefined)); }, []);
  return <><PageHeading eyebrow="EVIDENCE-BASED PROFILE" title="나의 스펙정보" body="입력한 정보는 사람인 공고의 필수·우대 조건과 비교하는 추천 근거로 사용됩니다." />{saved && <div className="account-alert">스펙정보를 저장했습니다.</div>}<section className="panel profile-form-panel"><CareerProfileForm initial={profile} onSave={async (value) => { const result = await saveCareerProfile(value); setProfile(result); setSaved(true); }} /></section></>;
}

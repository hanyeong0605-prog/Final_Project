import { useState } from "react";
import { useAuth } from "../../auth/model/AuthContext";
import { saveCareerProfile, skipCareerProfile } from "../api/careerProfileApi";
import { saveMemberSkills } from "../api/memberSkillsApi";
import { saveMemberCertificates } from "../api/memberCertificatesApi";
import { CareerProfileForm } from "./CareerProfileForm";

export function OnboardingModal() {
  const { member, updateMember } = useAuth();
  const [skipping, setSkipping] = useState(false);
  if (!member || member.onboardingCompleted) return null;

  return <div className="modal-backdrop"><section className="onboarding-modal">
    <div className="onboarding-head"><span className="eyebrow">PERSONALIZED RECOMMENDATION</span><h2>사용자 추천을 위한 정보를 입력해 주세요</h2><p>희망 직무와 현재 기술 경험을 바탕으로 채용공고의 지원 준비도를 비교합니다. 나중에 나의 스펙정보에서 언제든 수정할 수 있습니다.</p></div>
    <CareerProfileForm onSave={async (value, skills, certificates) => {
      await saveCareerProfile(value);
      await saveMemberSkills(skills.map(({ skillId, selfReportedLevel, note }) => ({ skillId, selfReportedLevel, note })));
      await saveMemberCertificates(certificates.map(({ name, issuer, acquiredAt, expiresAt, officialUrl }) => ({ name, issuer, acquiredAt, expiresAt, officialUrl })));
      updateMember({ ...member, onboardingCompleted: true });
    }} saveLabel="입력하고 추천 시작하기" />
    <button className="skip-onboarding" disabled={skipping} onClick={() => { setSkipping(true); void skipCareerProfile().then(updateMember).finally(() => setSkipping(false)); }}>{skipping ? "처리 중..." : "괜찮아요, 전체 공고를 볼게요"}</button>
  </section></div>;
}

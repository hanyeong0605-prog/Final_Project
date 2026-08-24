import { useState } from "react";
import { ArrowRight } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/model/AuthContext";
import { skipCareerProfile } from "../api/careerProfileApi";

export function OnboardingModal() {
  const { member, updateMember } = useAuth();
  const [skipping, setSkipping] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const navigate = useNavigate();
  if (!member || member.onboardingCompleted || dismissed) return null;

  return <div className="modal-backdrop"><section className="onboarding-modal onboarding-choice" role="dialog" aria-modal="true" aria-labelledby="onboarding-title">
    <img className="onboarding-choice-mascot" src="/mascot/mascot-binoculars-transparent.png" alt="쌍안경으로 공고를 찾는 잡아드림 고양이" />
    <div className="onboarding-head"><span className="eyebrow">PERSONALIZED RECOMMENDATION</span><h2 id="onboarding-title">내 스펙으로 더 잘 맞는 공고를 찾아볼까요?</h2><p>희망 직무와 보유 기술을 입력하면 나에게 맞는 채용공고와 지원 준비도를 추천해 드립니다. 스펙은 마이페이지에서 언제든 수정할 수 있어요.</p></div>
    <button className="primary-button onboarding-start" onClick={() => { setDismissed(true); navigate("/account?editSpec=1"); }}>내 스펙 입력하고 맞춤 추천받기<ArrowRight size={17} /></button>
    <button className="skip-onboarding" disabled={skipping} onClick={() => { setSkipping(true); void skipCareerProfile().then(updateMember).finally(() => setSkipping(false)); }}>{skipping ? "처리 중..." : "나중에 하기"}</button>
  </section></div>;
}

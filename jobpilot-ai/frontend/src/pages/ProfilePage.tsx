import { Plus } from "lucide-react";
import { ProfileSummary } from "../features/profile/components/ProfileSummary";
import { memberProfileFixture } from "../features/profile/data/profile.fixture";
import { PageHeading } from "../shared/components/PageHeading";

function Tip({ done, text }: { done?: boolean; text: string }) {
  return <div className={done ? "tip done" : "tip"}><span>{done ? "✓" : ""}</span>{text}</div>;
}

export function ProfilePage() {
  return <><PageHeading eyebrow="EVIDENCE-BASED PROFILE" title="점수가 아닌, 증명 가능한 역량을 관리하세요." body="기술을 보유했다고만 적지 않고 프로젝트·교육·자격증·GitHub 연결을 근거로 저장합니다." action={<button className="primary-button"><Plus size={17} />프로젝트 근거 추가</button>} /><div className="profile-layout"><ProfileSummary profile={memberProfileFixture} /><aside className="profile-tips"><span className="eyebrow">PROFILE QUALITY</span><h2>공고 매칭을 더 정확하게 하려면</h2><Tip done text="희망 직무와 지원 가능 지역" /><Tip done text="프로젝트의 사용 기술 연결" /><Tip done text="GitHub 저장소 1개 연결" /><Tip text="AWS·Docker 배포 링크 또는 설명" /><Tip text="자격증 또는 교육 수료 이력" /><p>기술명만 입력한 경우보다, 프로젝트·교육·자격 근거가 연결될수록 매칭 결과가 선명해집니다.</p></aside></div></>;
}

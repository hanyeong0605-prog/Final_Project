import { FormEvent, useEffect, useState } from "react";
import { Bookmark, ChevronRight, KeyRound, Pencil, Target, UserX } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { changeNickname, changePassword, withdraw } from "../features/auth/api/accountApi";
import { useAuth } from "../features/auth/model/AuthContext";
import { getBookmarkedJobs } from "../features/interests/api/interestsApi";
import { getBookmarkedOpportunities } from "../features/opportunities/api/opportunityInterestsApi";
import { OpportunityCard } from "../features/opportunities/components/OpportunityCard";
import type { Opportunity } from "../features/opportunities/model/opportunity.types";
import { useInterests } from "../features/interests/model/InterestContext";
import { JobPostingCard } from "../features/job-postings/components/JobPostingCard";
import type { JobPosting } from "../features/job-postings/model/jobPosting.types";
import { getCareerProfile, saveCareerProfile } from "../features/profile/api/careerProfileApi";
import { getMemberSkills, saveMemberSkills } from "../features/profile/api/memberSkillsApi";
import { getMemberCertificates, saveMemberCertificates } from "../features/profile/api/memberCertificatesApi";
import { CareerProfileForm } from "../features/profile/components/CareerProfileForm";
import { SubscriptionSection } from "../features/subscription/components/SubscriptionSection";
import { PushNotificationSection } from "../features/push-notifications/components/PushNotificationSection";
import type { CareerProfile } from "../features/profile/model/careerProfile.types";
import type { MemberSkill } from "../features/profile/model/memberSkill.types";
import type { MemberCertificate } from "../features/profile/model/memberCertificate.types";
import { PageHeading } from "../shared/components/PageHeading";

type Action = "nickname" | "password" | "withdraw" | "spec" | null;
export function MyPage() {
  const { member, loading, updateMember, logout } = useAuth(); const { interestCount } = useInterests(); const navigate = useNavigate(); const location = useLocation();
  const [action, setAction] = useState<Action>(null); const [nickname, setNickname] = useState(member?.nickname ?? "");
  const [passwords, setPasswords] = useState({ current: "", next: "" }); const [withdrawPassword, setWithdrawPassword] = useState("");
  const [message, setMessage] = useState(""); const [error, setError] = useState(""); const [submitting, setSubmitting] = useState<Action>(null); const [jobs, setJobs] = useState<JobPosting[]>([]); const [opportunities, setOpportunities] = useState<Opportunity[]>([]); const [profile, setProfile] = useState<CareerProfile>(); const [memberSkills, setMemberSkills] = useState<MemberSkill[]>([]); const [memberCertificates, setMemberCertificates] = useState<MemberCertificate[]>([]);
  useEffect(() => { void getBookmarkedJobs().then(setJobs).catch(() => setJobs([])); }, [interestCount]);
  useEffect(() => { void getBookmarkedOpportunities().then(setOpportunities).catch(() => setOpportunities([])); }, []);
  useEffect(() => { if (!loading && !member) navigate(`/login?returnTo=${encodeURIComponent(`${location.pathname}${location.search}`)}`, { replace: true }); }, [loading, member, navigate, location.pathname, location.search]);
  useEffect(() => { if (new URLSearchParams(location.search).get("editSpec") === "1") setAction("spec"); }, [location.search]);
  useEffect(() => { void Promise.all([getCareerProfile(), getMemberSkills(), getMemberCertificates()]).then(([savedProfile, savedSkills, savedCertificates]) => { setProfile(savedProfile); setMemberSkills(savedSkills); setMemberCertificates(savedCertificates); }).catch(() => { setProfile(undefined); setMemberSkills([]); setMemberCertificates([]); }); }, []);
  const run = async (kind: Exclude<Action, null>, fn: () => Promise<void>, success: string) => { if (submitting) return; setError(""); setMessage(""); setSubmitting(kind); try { await fn(); setMessage(success); setAction(null); } catch (e) { setError(e instanceof Error ? e.message : "요청에 실패했습니다."); } finally { setSubmitting(null); } };
  const nicknameSubmit = (e: FormEvent) => { e.preventDefault(); void run("nickname", async () => updateMember(await changeNickname(nickname.trim())), "닉네임을 변경했습니다."); };
  const passwordSubmit = (e: FormEvent) => { e.preventDefault(); void run("password", async () => { await changePassword(passwords.current, passwords.next); setPasswords({ current: "", next: "" }); }, "비밀번호를 변경했습니다."); };
  const isOAuthOnly = Boolean(member && !member.passwordLoginEnabled);
  const withdrawalSubmit = (e: FormEvent) => { e.preventDefault(); if (!confirm("회원 정보와 일정이 모두 삭제됩니다. 정말 탈퇴할까요?")) return; void run("withdraw", async () => { await withdraw(isOAuthOnly ? undefined : withdrawPassword, isOAuthOnly ? withdrawPassword : undefined); logout(); navigate("/login", { replace: true }); }, ""); };
  return <><PageHeading eyebrow="MY ACCOUNT" title="마이페이지" body="나의 정보, 스펙과 찜한 채용공고를 관리합니다." />
    {(message || error) && <div className={error ? "account-alert error" : "account-alert"}>{error || message}</div>}
    <div className="mypage-section-title"><h2>나의 정보</h2><p>계정 기본 정보를 관리합니다.</p></div>
    <section className="panel account-summary"><div className="avatar large">{member?.nickname.slice(0, 1)}</div><div><h2>{member?.nickname}</h2><p>{member?.loginId} · {member?.email}</p></div></section>
    <section className="account-actions"><button aria-expanded={action === "nickname"} onClick={() => setAction(action === "nickname" ? null : "nickname")}><Pencil size={18} /><span><strong>닉네임 변경</strong><small>서비스에 표시되는 이름을 변경합니다.</small></span><ChevronRight size={17} /></button><button aria-expanded={action === "password"} onClick={() => setAction(action === "password" ? null : "password")}><KeyRound size={18} /><span><strong>비밀번호 변경</strong><small>{isOAuthOnly ? "소셜 계정 비밀번호는 제공사에서 관리합니다." : "현재 비밀번호 확인 후 변경합니다."}</small></span><ChevronRight size={17} /></button><button className="withdraw-action" aria-expanded={action === "withdraw"} onClick={() => setAction(action === "withdraw" ? null : "withdraw")}><UserX size={18} /><span><strong>회원 탈퇴</strong><small>회원 데이터와 개인 일정을 삭제합니다.</small></span><ChevronRight size={17} /></button></section>
    {action === "nickname" &&<section className="panel account-editor"><h2>닉네임 변경</h2><form onSubmit={nicknameSubmit}><label>새 닉네임<input required minLength={2} maxLength={80} value={nickname} onChange={(e) => setNickname(e.target.value)} /></label><button className="primary-button" disabled={submitting === "nickname"}>{submitting === "nickname" ? "변경 중..." : "변경하기"}</button></form></section>}
    {action === "password" && <section className="panel account-editor"><h2>비밀번호 변경</h2>{isOAuthOnly ? <p>소셜 로그인 계정에는 Job-A-Dream 비밀번호가 없습니다. 비밀번호 변경은 네이버·카카오·구글 계정 설정에서 진행해 주세요.</p> : <form onSubmit={passwordSubmit}><label>현재 비밀번호<input required type="password" autoComplete="current-password" value={passwords.current} onChange={(e) => setPasswords({ ...passwords, current: e.target.value })} /></label><label>새 비밀번호<input required type="password" autoComplete="new-password" minLength={8} maxLength={72} value={passwords.next} onChange={(e) => setPasswords({ ...passwords, next: e.target.value })} /></label><button className="primary-button" disabled={submitting === "password"}>{submitting === "password" ? "변경 중..." : "변경하기"}</button></form>}</section>}
    {action === "withdraw" && <section className="panel account-editor danger-zone"><h2>회원 탈퇴</h2><p>탈퇴하면 회원 프로필, 매칭 결과, 찜과 일정이 모두 삭제됩니다.</p><form onSubmit={withdrawalSubmit}><label>{isOAuthOnly ? "확인 문구" : "비밀번호 확인"}<input required type={isOAuthOnly ? "text" : "password"} autoComplete={isOAuthOnly ? "off" : "current-password"} placeholder={isOAuthOnly ? "회원탈퇴" : undefined} value={withdrawPassword} onChange={(e) => setWithdrawPassword(e.target.value)} /></label><button className="danger-button" disabled={submitting === "withdraw"}>{submitting === "withdraw" ? "탈퇴 처리 중..." : "회원 탈퇴 진행"}</button></form></section>}
    <SubscriptionSection />
    <PushNotificationSection />
    <div className="mypage-section-title spec-title"><div><h2>나의 스펙정보</h2><p>공고 추천과 지원 준비도 비교에 사용됩니다.</p></div><button className="outline-button" onClick={() => setAction(action === "spec" ? null : "spec")}><Target size={16} />{profile ? "스펙 수정" : "스펙 입력"}</button></div>
    {action === "spec" && <section className="panel account-editor spec-editor"><CareerProfileForm initial={profile} initialSkills={memberSkills} initialCertificates={memberCertificates} onCancel={() => setAction(null)} onSave={async (value, skills, certificates) => { const saved = await saveCareerProfile(value); const savedSkills = await saveMemberSkills(skills.map(({ skillId, selfReportedLevel, note }) => ({ skillId, selfReportedLevel, note }))); const savedCertificates = await saveMemberCertificates(certificates.map(({ name, issuer, acquiredAt, expiresAt, officialUrl }) => ({ name, issuer, acquiredAt, expiresAt, officialUrl }))); setProfile(saved); setMemberSkills(savedSkills); setMemberCertificates(savedCertificates); if (member) updateMember({ ...member, onboardingCompleted: true }); setMessage("스펙정보, 보유 기술, 자격증을 저장했습니다."); setAction(null); }} /></section>}
    {!profile && action !== "spec" && <section className="panel saved-empty">아직 등록한 스펙정보가 없습니다.</section>}
    {profile && action !== "spec" && <section className="panel spec-summary"><div><span>목표 직무</span><strong>{profile.targetRole}</strong></div><div><span>희망 지역</span><strong>{profile.preferredLocations.join(", ") || "미입력"}</strong></div><div><span>경력</span><strong>{profile.totalCareerMonths}개월</strong></div><div><span>기술 요약</span><strong>{profile.technicalSummary || "미입력"}</strong></div></section>}
    <div className="saved-jobs-title"><div><Bookmark size={18} /><h2>나의 채용공고 찜 목록</h2></div><span>{jobs.length}개</span></div>
    {jobs.length === 0 ? <section className="panel saved-empty">찜한 채용공고가 없습니다.</section> : <section className="posting-grid">{jobs.map((job) => <JobPostingCard key={job.id} posting={job} />)}</section>}
    <div className="saved-jobs-title"><div><Bookmark size={18} /><h2>찜한 성장 기회</h2></div><span>{opportunities.length}개</span></div>
    {opportunities.length === 0 ? <section className="panel saved-empty">찜한 훈련과정·성장 기회가 없습니다.</section> : <section className="opportunity-grid">{opportunities.map((item) => <OpportunityCard key={item.id} item={item} interested onInterest={() => {}} />)}</section>}
  </>;
}

import { FormEvent, useEffect, useState } from "react";
import { Bookmark, ChevronRight, Heart, KeyRound, MessageCircle, Pencil, Target, UserX } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { changeNickname, changePassword, withdraw } from "../features/auth/api/accountApi";
import { useAuth } from "../features/auth/model/AuthContext";
import { getBookmarkedJobs } from "../features/interests/api/interestsApi";
import { getBookmarkedOpportunities, toggleOpportunityInterest } from "../features/opportunities/api/opportunityInterestsApi";
import { OpportunityCard } from "../features/opportunities/components/OpportunityCard";
import type { Opportunity } from "../features/opportunities/model/opportunity.types";
import { useInterests } from "../features/interests/model/InterestContext";
import { JobPostingCard } from "../features/job-postings/components/JobPostingCard";
import type { JobPosting } from "../features/job-postings/model/jobPosting.types";
import { SubscriptionSection } from "../features/subscription/components/SubscriptionSection";
import { PushNotificationSection } from "../features/push-notifications/components/PushNotificationSection";
import { PageHeading } from "../shared/components/PageHeading";
import { SavedCapabilityList } from "./CapabilityManagementPage";
import { getCertificateBookmarks, removeCertificateBookmark, type QnetQualification } from "../features/profile/api/memberCertificatesApi";
import { CertificateDetailModal } from "../features/profile/components/CertificateDetailModal";
import { like, likeComment, myActivities, type CommunityActivity } from "../features/community/api/communityApi";

type Action = "nickname" | "password" | "withdraw" | null;
export function MyPage() {
  const { member, loading, updateMember, logout } = useAuth(); const { interestCount, interestIds } = useInterests(); const navigate = useNavigate(); const location = useLocation();
  const [action, setAction] = useState<Action>(null); const [nickname, setNickname] = useState(member?.nickname ?? "");
  const [passwords, setPasswords] = useState({ current: "", next: "" }); const [withdrawPassword, setWithdrawPassword] = useState(""); const [activities, setActivities] = useState<CommunityActivity[]>([]);
  const [message, setMessage] = useState(""); const [error, setError] = useState(""); const [submitting, setSubmitting] = useState<Action>(null); const [jobs, setJobs] = useState<JobPosting[]>([]); const [opportunities, setOpportunities] = useState<Opportunity[]>([]); const [certificateBookmarks, setCertificateBookmarks] = useState<QnetQualification[]>([]); const [certificateDetail, setCertificateDetail] = useState<QnetQualification | null>(null);
  useEffect(() => { void getBookmarkedJobs().then(setJobs).catch(() => setJobs([])); }, [interestCount]);
  useEffect(() => { void getBookmarkedOpportunities().then(setOpportunities).catch(() => setOpportunities([])); }, []);
  useEffect(() => { void getCertificateBookmarks().then(setCertificateBookmarks).catch(() => setCertificateBookmarks([])); }, []);
  const refreshActivities = () => myActivities().then((data) => setActivities(data.items)).catch(() => setActivities([]));
  useEffect(() => { if (member) void refreshActivities(); }, [member]);
  const cancelCommunityLike = async (activity: CommunityActivity) => {
    if (activity.type === "POST_LIKE") await like(activity.targetId);
    if (activity.type === "COMMENT_LIKE") await likeComment(activity.targetId);
    await refreshActivities();
  };
  // 2026-08-26: onInterest={() => {}}로 아무 동작도 안 하던 버그 - 찜한 성장 기회 목록에서
  // 버튼을 눌러도 API 호출도 로컬 상태 변경도 없었다. 목록에 이미 있는 항목이니 누르면
  // "찜 해제"가 맞는 동작이라, 낙관적으로 목록에서 바로 빼고 실패하면 다시 불러와 복구한다.
  const removeBookmarkedOpportunity = (id: number) => {
    setOpportunities((current) => current.filter((item) => item.id !== id));
    void toggleOpportunityInterest(id, false).catch(() => {
      void getBookmarkedOpportunities().then(setOpportunities).catch(() => {});
    });
  };
  useEffect(() => { if (!loading && !member) navigate(`/login?returnTo=${encodeURIComponent(`${location.pathname}${location.search}`)}`, { replace: true }); }, [loading, member, navigate, location.pathname, location.search]);
  const run = async (kind: Exclude<Action, null>, fn: () => Promise<void>, success: string) => { if (submitting) return; setError(""); setMessage(""); setSubmitting(kind); try { await fn(); setMessage(success); setAction(null); } catch (e) { setError(e instanceof Error ? e.message : "요청에 실패했습니다."); } finally { setSubmitting(null); } };
  const nicknameSubmit = (e: FormEvent) => { e.preventDefault(); void run("nickname", async () => updateMember(await changeNickname(nickname.trim())), "닉네임을 변경했습니다."); };
  const passwordSubmit = (e: FormEvent) => { e.preventDefault(); void run("password", async () => { await changePassword(passwords.current, passwords.next); setPasswords({ current: "", next: "" }); }, "비밀번호를 변경했습니다."); };
  const isOAuthOnly = Boolean(member && !member.passwordLoginEnabled);
  const withdrawalSubmit = (e: FormEvent) => { e.preventDefault(); if (!confirm("회원 정보와 일정이 모두 삭제됩니다. 정말 탈퇴할까요?")) return; void run("withdraw", async () => { await withdraw(isOAuthOnly ? undefined : withdrawPassword, isOAuthOnly ? withdrawPassword : undefined); logout(); navigate("/login", { replace: true }); }, ""); };
  return <><PageHeading eyebrow="MY ACCOUNT" title="마이페이지" body="나의 정보, 스펙과 찜한 채용공고를 관리합니다." />
    {(message || error) && <div className={error ? "account-alert error" : "account-alert"}>{error || message}</div>}
    <div className="mypage-section-title"><h2>나의 정보</h2><p>계정 기본 정보를 관리합니다.</p></div>
    <section className="panel account-summary"><div className="avatar large">{member?.nickname.slice(0, 1)}</div><div><h2>{member?.nickname}</h2><p>{member?.loginId} · {member?.email}</p></div></section>
    <section className={isOAuthOnly ? "account-actions oauth-only" : "account-actions"}>{!isOAuthOnly && <><button aria-expanded={action === "nickname"} onClick={() => setAction(action === "nickname" ? null : "nickname")}><Pencil size={18} /><span><strong>닉네임 변경</strong><small>서비스에 표시되는 이름을 변경합니다.</small></span><ChevronRight size={17} /></button><button aria-expanded={action === "password"} onClick={() => setAction(action === "password" ? null : "password")}><KeyRound size={18} /><span><strong>비밀번호 변경</strong><small>현재 비밀번호 확인 후 변경합니다.</small></span><ChevronRight size={17} /></button></>}<button className="withdraw-action" aria-expanded={action === "withdraw"} onClick={() => setAction(action === "withdraw" ? null : "withdraw")}><UserX size={18} /><span><strong>회원 탈퇴</strong><small>{isOAuthOnly ? "소셜 로그인 계정은 회원 탈퇴만 가능합니다." : "회원 데이터와 개인 일정을 삭제합니다."}</small></span><ChevronRight size={17} /></button></section>
    {!isOAuthOnly && action === "nickname" &&<section className="panel account-editor"><h2>닉네임 변경</h2><form onSubmit={nicknameSubmit}><label>새 닉네임<input required minLength={2} maxLength={80} value={nickname} onChange={(e) => setNickname(e.target.value)} /></label><button className="primary-button" disabled={submitting === "nickname"}>{submitting === "nickname" ? "변경 중..." : "변경하기"}</button></form></section>}
    {!isOAuthOnly && action === "password" && <section className="panel account-editor"><h2>비밀번호 변경</h2><form onSubmit={passwordSubmit}><label>현재 비밀번호<input required type="password" autoComplete="current-password" value={passwords.current} onChange={(e) => setPasswords({ ...passwords, current: e.target.value })} /></label><label>새 비밀번호<input required type="password" autoComplete="new-password" minLength={8} maxLength={72} value={passwords.next} onChange={(e) => setPasswords({ ...passwords, next: e.target.value })} /></label><button className="primary-button" disabled={submitting === "password"}>{submitting === "password" ? "변경 중..." : "변경하기"}</button></form></section>}
    {action === "withdraw" && <section className="panel account-editor danger-zone"><h2>회원 탈퇴</h2><p>탈퇴하면 회원 프로필, 매칭 결과, 찜과 일정이 모두 삭제됩니다.</p><form onSubmit={withdrawalSubmit}><label>{isOAuthOnly ? "확인 문구" : "비밀번호 확인"}<input required type={isOAuthOnly ? "text" : "password"} autoComplete={isOAuthOnly ? "off" : "current-password"} placeholder={isOAuthOnly ? "회원탈퇴" : undefined} value={withdrawPassword} onChange={(e) => setWithdrawPassword(e.target.value)} /></label><button className="danger-button" disabled={submitting === "withdraw"}>{submitting === "withdraw" ? "탈퇴 처리 중..." : "회원 탈퇴 진행"}</button></form></section>}
    <SubscriptionSection />
    <PushNotificationSection />
    <div className="mypage-section-title spec-title"><div><h2>나의 스펙정보</h2><p>역량 관리에 저장한 스펙정보를 조회합니다.</p></div><button className="outline-button" onClick={() => navigate("/capability?tool=profile")}><Target size={16} />스펙정보 입력하기</button></div>
    <section className="panel mypage-capability-view"><SavedCapabilityList readOnly /></section>
    <div className="saved-jobs-title"><div><MessageCircle size={18} /><h2>커뮤니티 활동</h2></div><span>{activities.length}건</span></div>
    <section className="panel community-activity-list">{activities.length === 0 ? <div className="saved-empty">작성한 글, 댓글 또는 좋아요 활동이 없습니다.</div> : activities.map((activity) => <article key={`${activity.type}-${activity.targetId}-${activity.createdAt}`}><button type="button" className="community-activity-open" onClick={() => navigate(`/community/${activity.postId ?? activity.targetId}`)}>{activity.type.includes("LIKE") ? <Heart size={15} /> : <MessageCircle size={15} />}<div><strong>{{ POST: "작성한 글", COMMENT: "작성한 댓글", POST_LIKE: "글 좋아요", COMMENT_LIKE: "댓글 좋아요" }[activity.type]}</strong><span>{activity.title}</span><small>{new Date(activity.createdAt).toLocaleString("ko-KR")}{activity.status !== "PUBLIC" ? " · 비공개/관리됨" : ""}</small></div></button>{activity.type.includes("LIKE") ? <button type="button" className="outline-button community-activity-action" onClick={() => void cancelCommunityLike(activity)}>좋아요 취소</button> : <button type="button" className="outline-button community-activity-action" onClick={() => navigate(`/community/${activity.postId ?? activity.targetId}`)}>관리</button>}</article>)}</section>
    <div className="saved-jobs-title"><div><Bookmark size={18} /><h2>찜한 자격증</h2></div><span>{certificateBookmarks.length}개</span></div>
    {certificateBookmarks.length === 0 ? <section className="panel saved-empty">찜한 자격증이 없습니다.</section> : <section className="certificate-opportunity-grid">{certificateBookmarks.map((item) => <article className="certificate-opportunity-card" key={item.code}><div><strong>{item.name}</strong><span>{[item.qualificationType, item.field, item.subField].filter(Boolean).join(" · ")}</span></div><div className="certificate-opportunity-actions"><button type="button" className="certificate-opportunity-detail" onClick={() => setCertificateDetail(item)}>시험 일정</button><button type="button" className="bookmark active" aria-label="자격증 찜 해제" onClick={() => void removeCertificateBookmark(item.code).then(setCertificateBookmarks)}><Bookmark size={17} fill="currentColor" /></button></div></article>)}</section>}
    <div className="saved-jobs-title"><div><Bookmark size={18} /><h2>나의 채용공고 찜 목록</h2></div><span>{jobs.filter((job) => interestIds.includes(job.id)).length}개</span></div>
    {jobs.filter((job) => interestIds.includes(job.id)).length === 0 ? <section className="panel saved-empty">찜한 채용공고가 없습니다.</section> : <section className="posting-grid">{jobs.filter((job) => interestIds.includes(job.id)).map((job) => <JobPostingCard key={job.id} posting={job} />)}</section>}
    <div className="saved-jobs-title"><div><Bookmark size={18} /><h2>찜한 성장 기회</h2></div><span>{opportunities.length}개</span></div>
    {opportunities.length === 0 ? <section className="panel saved-empty">찜한 훈련과정·성장 기회가 없습니다.</section> : <section className="opportunity-grid">{opportunities.map((item) => <OpportunityCard key={item.id} item={item} interested onInterest={() => removeBookmarkedOpportunity(item.id)} />)}</section>}
    {certificateDetail && <CertificateDetailModal item={certificateDetail} onClose={() => setCertificateDetail(null)} />}
  </>;
}

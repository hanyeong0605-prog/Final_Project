import { useEffect, useState } from "react";
import { ShieldCheck, UsersRound, BriefcaseBusiness, CircleCheckBig, Search } from "lucide-react";
import { PageHeading } from "../shared/components/PageHeading";
import { changeAdminJobPostingStatus, changeAdminMemberRole, getAdminJobPostings, getAdminMembers, getAdminOverview, type AdminJobPosting, type AdminMember, type AdminOverview } from "../features/admin/api/adminApi";

export function AdminPage() {
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [members, setMembers] = useState<AdminMember[]>([]);
  const [postings, setPostings] = useState<AdminJobPosting[]>([]);
  const [memberQuery, setMemberQuery] = useState("");
  const [postingQuery, setPostingQuery] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const load = async () => {
    setError("");
    try {
      const [summary, memberPage, postingPage] = await Promise.all([getAdminOverview(), getAdminMembers(memberQuery), getAdminJobPostings(postingQuery)]);
      setOverview(summary); setMembers(memberPage.content); setPostings(postingPage.content);
    } catch (e) { setError(e instanceof Error ? e.message : "관리자 데이터를 불러오지 못했습니다."); }
  };
  useEffect(() => { void load(); }, []);

  const updateRole = async (member: AdminMember) => {
    setError(""); setNotice("");
    try {
      const next = await changeAdminMemberRole(member.id, member.role === "ADMIN" ? "USER" : "ADMIN");
      setMembers((current) => current.map((item) => item.id === next.id ? next : item));
      setNotice(`${next.nickname} 계정의 권한을 ${next.role === "ADMIN" ? "관리자" : "일반 회원"}으로 변경했습니다.`);
      void getAdminOverview().then(setOverview);
    } catch (e) { setError(e instanceof Error ? e.message : "권한 변경에 실패했습니다."); }
  };
  const updateStatus = async (posting: AdminJobPosting, status: AdminJobPosting["status"]) => {
    setError(""); setNotice("");
    try {
      const next = await changeAdminJobPostingStatus(posting.id, status);
      setPostings((current) => current.map((item) => item.id === next.id ? next : item));
      setNotice(`“${next.title}” 공고 상태를 ${status}로 변경했습니다.`);
      void getAdminOverview().then(setOverview);
    } catch (e) { setError(e instanceof Error ? e.message : "공고 상태 변경에 실패했습니다."); }
  };

  return <>
    <PageHeading eyebrow="ADMIN CONSOLE" title="관리자 페이지" body="회원, 채용공고, 운영 현황을 안전하게 관리합니다." />
    {(notice || error) && <div className={error ? "account-alert error" : "account-alert"}>{error || notice}</div>}
    <section className="admin-metric-grid">
      <article><UsersRound size={20} /><span>전체 회원</span><strong>{overview?.memberCount ?? "-"}</strong></article>
      <article><ShieldCheck size={20} /><span>관리자</span><strong>{overview?.adminCount ?? "-"}</strong></article>
      <article><BriefcaseBusiness size={20} /><span>전체 공고</span><strong>{overview?.jobPostingCount ?? "-"}</strong></article>
      <article><CircleCheckBig size={20} /><span>활성 공고</span><strong>{overview?.activePostingCount ?? "-"}</strong></article>
    </section>
    <section className="panel admin-panel">
      <div className="admin-panel-heading"><div><span className="eyebrow">MEMBER MANAGEMENT</span><h2>회원 관리</h2></div><form onSubmit={(event) => { event.preventDefault(); void load(); }}><Search size={16} /><input value={memberQuery} onChange={(event) => setMemberQuery(event.target.value)} placeholder="아이디·이메일·닉네임 검색" /><button className="outline-button">검색</button></form></div>
      <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>회원</th><th>이메일</th><th>온보딩</th><th>권한</th><th>관리</th></tr></thead><tbody>{members.map((member) => <tr key={member.id}><td><strong>{member.nickname}</strong><small>{member.loginId}</small></td><td>{member.email}</td><td>{member.onboardingCompleted ? "완료" : "미완료"}</td><td><span className={`admin-role-badge ${member.role === "ADMIN" ? "admin" : ""}`}>{member.role === "ADMIN" ? "관리자" : "회원"}</span></td><td><button className="outline-button" onClick={() => void updateRole(member)}>{member.role === "ADMIN" ? "관리자 해제" : "관리자 지정"}</button></td></tr>)}</tbody></table></div>
    </section>
    <section className="panel admin-panel">
      <div className="admin-panel-heading"><div><span className="eyebrow">JOB POSTING MANAGEMENT</span><h2>채용공고 관리</h2></div><form onSubmit={(event) => { event.preventDefault(); void load(); }}><Search size={16} /><input value={postingQuery} onChange={(event) => setPostingQuery(event.target.value)} placeholder="공고명·회사명 검색" /><button className="outline-button">검색</button></form></div>
      <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>공고</th><th>근무지</th><th>조회</th><th>상태</th><th>관리</th></tr></thead><tbody>{postings.map((posting) => <tr key={posting.id}><td><strong>{posting.title}</strong><small>{posting.companyName ?? "회사 정보 없음"}</small></td><td>{posting.location ?? "-"}</td><td>{posting.viewCount.toLocaleString()}</td><td><span className={`admin-role-badge status-${posting.status.toLowerCase()}`}>{posting.status}</span></td><td><select value={posting.status} onChange={(event) => void updateStatus(posting, event.target.value as AdminJobPosting["status"])}><option value="ACTIVE">공개</option><option value="HIDDEN">숨김</option><option value="CLOSED">마감</option></select></td></tr>)}</tbody></table></div>
    </section>
  </>;
}

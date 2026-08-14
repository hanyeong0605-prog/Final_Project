import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { BriefcaseBusiness, CheckSquare, CircleCheckBig, Pencil, Search, ShieldCheck, Trash2, UsersRound } from "lucide-react";
import { PageHeading } from "../shared/components/PageHeading";
import { AdminFaceAuthModal } from "../features/admin/components/AdminFaceAuthModal";
import {
  changeAdminJobPostingStatus,
  changeAdminJobPostingStatuses,
  changeAdminMemberRole,
  changeAdminMemberRoles,
  deleteAdminJobPosting,
  getAdminJobPostings,
  getAdminMembers,
  getAdminOverview,
  updateAdminJobPosting,
  type AdminJobPosting,
  type AdminMember,
  type AdminOverview,
  type AdminPage as AdminPageData,
  type MemberRole,
} from "../features/admin/api/adminApi";

const PAGE_SIZE = 20;

function toDateTimeInput(value: string | null) {
  return value ? value.slice(0, 16) : "";
}

export function AdminPage() {
  const navigate = useNavigate();

  // 로그인 스토리지에서 관리자 식별자 추출 (미인증/더미 시 local-dev 대체)
  const currentAdminId = (() => {
    try {
      const stored = localStorage.getItem("user") || sessionStorage.getItem("user");
      if (stored) {
        const parsed = JSON.parse(stored);
        return parsed.loginId || parsed.username || parsed.id || "local-dev";
      }
    } catch {
      // JSON 파싱 실패 무시
    }
    return "local-dev";
  })();

  // 2차 인증 상태 (페이지 진입 시마다 재인증 강제)
  const [isVerified, setIsVerified] = useState(false);
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(true);

  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [members, setMembers] = useState<AdminMember[]>([]);
  const [postingPage, setPostingPage] = useState<AdminPageData<AdminJobPosting>>({
    content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0,
  });

  const [memberQuery, setMemberQuery] = useState("");
  const [postingQuery, setPostingQuery] = useState("");
  const [postingStatus, setPostingStatus] = useState("ALL");
  const [postingSort, setPostingSort] = useState("deadline_asc");
  const [editing, setEditing] = useState<AdminJobPosting | null>(null);
  const [selectedMemberIds, setSelectedMemberIds] = useState<number[]>([]);
  const [selectedPostingIds, setSelectedPostingIds] = useState<number[]>([]);
  const [memberBulkRole, setMemberBulkRole] = useState<MemberRole>("USER");
  const [postingBulkStatus, setPostingBulkStatus] = useState<AdminJobPosting["status"]>("HIDDEN");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  const allMembersSelected = useMemo(
    () => members.length > 0 && members.every((m) => selectedMemberIds.includes(m.id)),
    [members, selectedMemberIds]
  );
  const allPostingsSelected = useMemo(
    () => postingPage.content.length > 0 && postingPage.content.every((p) => selectedPostingIds.includes(p.id)),
    [postingPage.content, selectedPostingIds]
  );

  const loadOverviewAndMembers = async () => {
    const [summary, memberData] = await Promise.all([getAdminOverview(), getAdminMembers(memberQuery)]);
    setOverview(summary);
    setMembers(memberData.content);
    setSelectedMemberIds([]);
  };

  const loadPostings = async (page = 0) => {
    const result = await getAdminJobPostings(postingQuery, page, PAGE_SIZE, postingStatus, postingSort);
    setPostingPage(result);
    setSelectedPostingIds([]);
  };

  const loadAllData = async () => {
    setError("");
    try {
      await Promise.all([loadOverviewAndMembers(), loadPostings(0)]);
    } catch (e) {
      setError(e instanceof Error ? e.message : "관리자 정보를 불러오지 못했습니다.");
    }
  };

  // 화상 인증 통과 시에만 데이터 로드
  useEffect(() => {
    if (isVerified) {
      void loadAllData();
    }
  }, [isVerified]);

  // 페이지 이탈 시 세션 토큰 초기화
  useEffect(() => {
    return () => {
      sessionStorage.removeItem("admin_face_verified");
    };
  }, []);

  const toggleSelect = (id: number, list: number[], setList: (ids: number[]) => void) => {
    setList(list.includes(id) ? list.filter((item) => item !== id) : [...list, id]);
  };

  const updateRole = async (member: AdminMember) => {
    setError(""); setNotice("");
    try {
      const next = await changeAdminMemberRole(member.id, member.role === "ADMIN" ? "USER" : "ADMIN");
      setNotice(`${next.nickname} 계정 권한을 변경했습니다.`);
      await loadOverviewAndMembers();
    } catch (e) {
      setError(e instanceof Error ? e.message : "권한 변경 실패");
    }
  };

  const applyMemberBulkRole = async () => {
    if (!selectedMemberIds.length) return;
    if (!window.confirm(`선택한 ${selectedMemberIds.length}명의 권한을 변경할까요?`)) return;
    setError(""); setNotice("");
    try {
      const result = await changeAdminMemberRoles(selectedMemberIds, memberBulkRole);
      setNotice(`${result.updatedCount}명의 권한을 변경했습니다.`);
      await loadOverviewAndMembers();
    } catch (e) {
      setError(e instanceof Error ? e.message : "일괄 변경 실패");
    }
  };

  const updateStatus = async (posting: AdminJobPosting, status: AdminJobPosting["status"]) => {
    setError(""); setNotice("");
    try {
      const next = await changeAdminJobPostingStatus(posting.id, status);
      setPostingPage((curr) => ({ ...curr, content: curr.content.map((p) => (p.id === next.id ? next : p)) }));
      setNotice("공고 상태를 변경했습니다.");
      void getAdminOverview().then(setOverview);
    } catch (e) {
      setError(e instanceof Error ? e.message : "상태 변경 실패");
    }
  };

  const applyPostingBulkStatus = async () => {
    if (!selectedPostingIds.length) return;
    if (!window.confirm(`선택한 ${selectedPostingIds.length}건의 상태를 변경할까요?`)) return;
    setError(""); setNotice("");
    try {
      const result = await changeAdminJobPostingStatuses(selectedPostingIds, postingBulkStatus);
      setNotice(`${result.updatedCount}건의 공고 상태를 일괄 변경했습니다.`);
      await Promise.all([loadPostings(postingPage.page), getAdminOverview().then(setOverview)]);
    } catch (e) {
      setError(e instanceof Error ? e.message : "일괄 상태 변경 실패");
    }
  };

  const savePosting = async () => {
    if (!editing) return;
    setError(""); setNotice("");
    try {
      const next = await updateAdminJobPosting(editing.id, {
        title: editing.title,
        companyName: editing.companyName,
        location: editing.location,
        deadlineAt: editing.deadlineAt || null,
        status: editing.status,
      });
      setPostingPage((curr) => ({ ...curr, content: curr.content.map((p) => (p.id === next.id ? next : p)) }));
      setEditing(null);
      setNotice("공고 정보를 수정했습니다.");
      void getAdminOverview().then(setOverview);
    } catch (e) {
      setError(e instanceof Error ? e.message : "수정 실패");
    }
  };

  const hidePosting = async (posting: AdminJobPosting) => {
    if (!window.confirm(`'${posting.title}' 공고를 숨김 처리할까요?`)) return;
    setError(""); setNotice("");
    try {
      await deleteAdminJobPosting(posting.id);
      setEditing(null);
      setNotice("공고를 숨김 처리했습니다.");
      await Promise.all([loadPostings(postingPage.page), getAdminOverview().then(setOverview)]);
    } catch (e) {
      setError(e instanceof Error ? e.message : "숨김 실패");
    }
  };

  return (
    <>
      {!isVerified && (
        <AdminFaceAuthModal
          isOpen={isAuthModalOpen}
          adminId={String(currentAdminId)}
          onClose={() => {
            setIsAuthModalOpen(false);
            navigate("/");
          }}
          onSuccess={() => {
            setIsVerified(true);
            setIsAuthModalOpen(false);
          }}
        />
      )}

      {isVerified && (
        <>
          <PageHeading eyebrow="ADMIN CONSOLE" title="관리자 페이지" body="회원, 채용공고, 일일 방문 현황을 안전하게 관리합니다." />
          {(notice || error) && <div className={error ? "account-alert error" : "account-alert"}>{error || notice}</div>}

          <section className="admin-metric-grid">
            <article><UsersRound size={20} /><span>전체 회원</span><strong>{overview?.memberCount ?? "-"}</strong></article>
            <article><ShieldCheck size={20} /><span>관리자</span><strong>{overview?.adminCount ?? "-"}</strong></article>
            <article><BriefcaseBusiness size={20} /><span>전체 공고</span><strong>{overview?.jobPostingCount ?? "-"}</strong></article>
            <article><CircleCheckBig size={20} /><span>공개 공고</span><strong>{overview?.activePostingCount ?? "-"}</strong></article>
            <article className="admin-visitor-metric"><CheckSquare size={20} /><span>오늘 방문 회원</span><strong>{overview?.todayVisitorCount ?? "-"}</strong><small>일반 {overview?.todayUserVisitorCount ?? "-"} · 관리자 {overview?.todayAdminVisitorCount ?? "-"}</small></article>
          </section>

          <section className="panel admin-panel">
            <div className="admin-panel-heading">
              <div><span className="eyebrow">MEMBER MANAGEMENT</span><h2>회원 관리</h2></div>
              <form onSubmit={(e) => { e.preventDefault(); void loadOverviewAndMembers(); }}>
                <Search size={16} /><input value={memberQuery} onChange={(e) => setMemberQuery(e.target.value)} placeholder="아이디·이메일·닉네임 검색" /><button className="outline-button">검색</button>
              </form>
            </div>
            <div className="admin-bulk-toolbar">
              <span>현재 목록에서 <b>{selectedMemberIds.length}</b>명 선택</span>
              <select value={memberBulkRole} onChange={(e) => setMemberBulkRole(e.target.value as MemberRole)}>
                <option value="USER">일반 회원으로 변경</option>
                <option value="ADMIN">관리자로 변경</option>
              </select>
              <button className="outline-button" disabled={!selectedMemberIds.length} onClick={() => void applyMemberBulkRole()}>선택 회원 일괄 적용</button>
            </div>
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th><input aria-label="현재 회원 목록 전체 선택" type="checkbox" checked={allMembersSelected} onChange={() => setSelectedMemberIds(allMembersSelected ? [] : members.map((m) => m.id))} /></th>
                    <th>회원</th><th>이메일</th><th>온보딩</th><th>권한</th><th>관리</th>
                  </tr>
                </thead>
                <tbody>
                  {members.map((member) => (
                    <tr key={member.id}>
                      <td><input aria-label={`${member.nickname} 선택`} type="checkbox" checked={selectedMemberIds.includes(member.id)} onChange={() => toggleSelect(member.id, selectedMemberIds, setSelectedMemberIds)} /></td>
                      <td><strong>{member.nickname}</strong><small>{member.loginId}</small></td>
                      <td>{member.email}</td>
                      <td>{member.onboardingCompleted ? "완료" : "미완료"}</td>
                      <td><span className={`admin-role-badge ${member.role === "ADMIN" ? "admin" : ""}`}>{member.role === "ADMIN" ? "관리자" : "회원"}</span></td>
                      <td><button className="outline-button" onClick={() => void updateRole(member)}>{member.role === "ADMIN" ? "관리자 해제" : "관리자 지정"}</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="panel admin-panel">
            <div className="admin-panel-heading admin-posting-heading">
              <div>
                <span className="eyebrow">JOB POSTING MANAGEMENT</span>
                <h2>채용공고 관리</h2>
                <p>마감일이 지난 공개 공고는 자동으로 마감 처리됩니다. 필요 시 선택한 공고를 한 번에 공개·마감·숨김 처리할 수 있습니다.</p>
              </div>
              <form onSubmit={(e) => { e.preventDefault(); void loadPostings(0); }}>
                <Search size={16} /><input value={postingQuery} onChange={(e) => setPostingQuery(e.target.value)} placeholder="공고명·회사명 검색" /><button className="outline-button">검색</button>
              </form>
            </div>
            <div className="admin-posting-filters">
              <label>상태<select value={postingStatus} onChange={(e) => setPostingStatus(e.target.value)}><option value="ALL">전체 상태</option><option value="ACTIVE">공개</option><option value="CLOSED">마감</option><option value="HIDDEN">숨김</option></select></label>
              <label>정렬<select value={postingSort} onChange={(e) => setPostingSort(e.target.value)}><option value="deadline_asc">마감 임박순</option><option value="deadline_desc">마감일 먼 순</option><option value="popular">조회수 높은 순</option><option value="recent">수집 최신순</option></select></label>
              <button className="outline-button" onClick={() => void loadPostings(0)}>필터 적용</button>
              <span>{postingPage.totalElements.toLocaleString()}건 중 {postingPage.content.length ? postingPage.page * postingPage.size + 1 : 0}–{Math.min((postingPage.page + 1) * postingPage.size, postingPage.totalElements)}건</span>
            </div>
            <div className="admin-bulk-toolbar">
              <span>현재 페이지에서 <b>{selectedPostingIds.length}</b>건 선택</span>
              <select value={postingBulkStatus} onChange={(e) => setPostingBulkStatus(e.target.value as AdminJobPosting["status"])}>
                <option value="ACTIVE">공개로 변경</option>
                <option value="CLOSED">마감으로 변경</option>
                <option value="HIDDEN">숨김으로 변경</option>
              </select>
              <button className="outline-button" disabled={!selectedPostingIds.length} onClick={() => void applyPostingBulkStatus()}>선택 공고 일괄 적용</button>
            </div>
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th><input aria-label="현재 공고 페이지 전체 선택" type="checkbox" checked={allPostingsSelected} onChange={() => setSelectedPostingIds(allPostingsSelected ? [] : postingPage.content.map((p) => p.id))} /></th>
                    <th>공고</th><th>근무지</th><th>마감일</th><th>조회</th><th>상태</th><th>관리</th>
                  </tr>
                </thead>
                <tbody>
                  {postingPage.content.map((posting) => {
                    const isEditing = editing?.id === posting.id;
                    const item = isEditing ? editing : posting;
                    return (
                      <tr key={posting.id}>
                        <td><input aria-label={`${posting.title} 선택`} type="checkbox" checked={selectedPostingIds.includes(posting.id)} onChange={() => toggleSelect(posting.id, selectedPostingIds, setSelectedPostingIds)} /></td>
                        <td>{isEditing ? <><input className="admin-inline-input" value={item.title} onChange={(e) => setEditing({ ...item, title: e.target.value })} /><input className="admin-inline-input" value={item.companyName ?? ""} onChange={(e) => setEditing({ ...item, companyName: e.target.value })} placeholder="회사명" /></> : <><button className="admin-job-link" onClick={() => navigate(`/job-postings/${posting.id}`)}>{posting.title}</button><small>{posting.companyName ?? "회사 정보 없음"}</small></>}</td>
                        <td>{isEditing ? <input className="admin-inline-input" value={item.location ?? ""} onChange={(e) => setEditing({ ...item, location: e.target.value })} placeholder="근무지" /> : posting.location ?? "-"}</td>
                        <td>{isEditing ? <input className="admin-inline-input" type="datetime-local" value={toDateTimeInput(item.deadlineAt)} onChange={(e) => setEditing({ ...item, deadlineAt: e.target.value || null })} /> : posting.deadlineAt ? new Date(posting.deadlineAt).toLocaleDateString("ko-KR") : "상시"}</td>
                        <td>{posting.viewCount.toLocaleString()}</td>
                        <td>{isEditing ? <select value={item.status} onChange={(e) => setEditing({ ...item, status: e.target.value as AdminJobPosting["status"] })}><option value="ACTIVE">공개</option><option value="HIDDEN">숨김</option><option value="CLOSED">마감</option></select> : <span className={`admin-role-badge status-${posting.status.toLowerCase()}`}>{posting.status}</span>}</td>
                        <td>
                          <div className="admin-table-actions">
                            {isEditing ? (
                              <>
                                <button className="outline-button" onClick={() => void savePosting()}>저장</button>
                                <button className="outline-button" onClick={() => setEditing(null)}>취소</button>
                              </>
                            ) : (
                              <>
                                <select value={posting.status} onChange={(e) => void updateStatus(posting, e.target.value as AdminJobPosting["status"])}><option value="ACTIVE">공개</option><option value="HIDDEN">숨김</option><option value="CLOSED">마감</option></select>
                                <button className="icon-button" title="공고 수정" onClick={() => setEditing(posting)}><Pencil size={15} /></button>
                              </>
                            )}
                            <button className="icon-button danger" title="공고 숨김" onClick={() => void hidePosting(posting)}><Trash2 size={15} /></button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            {postingPage.totalPages > 1 && (
              <div className="admin-pagination">
                <button className="outline-button" disabled={postingPage.page === 0} onClick={() => void loadPostings(postingPage.page - 1)}>이전</button>
                <span>{postingPage.page + 1} / {postingPage.totalPages} 페이지</span>
                <button className="outline-button" disabled={postingPage.page + 1 >= postingPage.totalPages} onClick={() => void loadPostings(postingPage.page + 1)}>다음</button>
              </div>
            )}
          </section>
        </>
      )}
    </>
  );
}
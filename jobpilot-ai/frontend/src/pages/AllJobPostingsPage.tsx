import { FormEvent, useEffect, useMemo, useState } from "react";
import { ChevronDown, Search, X } from "lucide-react";
import { getJobPostings } from "../features/job-postings/api/jobPostingsApi";
import { JobPostingCard } from "../features/job-postings/components/JobPostingCard";
import type { JobExperienceFilter, JobPostingPage, JobPostingSearchParams, JobPostingSort } from "../features/job-postings/model/jobPosting.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";

const PAGE_SIZE = 24;

const roleOptions = [
  ["BACKEND", "백엔드"], ["FRONTEND", "프론트엔드"], ["FULLSTACK", "풀스택"],
  ["MOBILE", "모바일"], ["DATA_AI", "데이터 · AI"], ["DEVOPS", "DevOps · 인프라"],
  ["QA", "QA · 테스트"], ["SECURITY", "보안"], ["GAME_EMBEDDED", "게임 · 임베디드"],
] as const;

const roleName = (value: string) => roleOptions.find(([key]) => key === value)?.[1] ?? value;
const sortLabel: Record<JobPostingSort, string> = {
  deadline_asc: "마감 임박순",
  deadline_desc: "마감일 늦은순",
  recent: "최근 수집순",
};

type Filters = {
  query: string;
  roles: string[];
  experience: JobExperienceFilter;
  location: string;
  employmentType: string;
  sort: JobPostingSort;
};

const initialFilters: Filters = {
  query: "", roles: [], experience: "", location: "", employmentType: "", sort: "deadline_asc",
};

export function AllJobPostingsPage() {
  const [query, setQuery] = useState("");
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [roleDraft, setRoleDraft] = useState<string[]>([]);
  const [roleMenuOpen, setRoleMenuOpen] = useState(false);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<JobPostingPage | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");

  useEffect(() => {
    let active = true;
    setStatus("loading");
    const params: JobPostingSearchParams = { ...filters, page, size: PAGE_SIZE };
    void getJobPostings(params)
      .then((data) => { if (active) { setResult(data); setStatus("ready"); } })
      .catch(() => { if (active) { setResult(null); setStatus("error"); } });
    return () => { active = false; };
  }, [filters, page]);

  const pages = useMemo(() => {
    if (!result || result.totalPages < 2) return [];
    const start = Math.max(0, Math.min(page - 2, result.totalPages - 5));
    return Array.from({ length: Math.min(5, result.totalPages) }, (_, index) => start + index);
  }, [page, result]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setFilters((current) => ({ ...current, query: query.trim() }));
    setPage(0);
  };

  const updateFilter = <Key extends keyof Omit<Filters, "query" | "roles">>(key: Key, value: Filters[Key]) => {
    setFilters((current) => ({ ...current, [key]: value }));
    setPage(0);
  };

  const toggleDraftRole = (role: string) => {
    setRoleDraft((current) => current.includes(role) ? current.filter((item) => item !== role) : [...current, role]);
  };

  const openRoleMenu = () => {
    setRoleDraft(filters.roles);
    setRoleMenuOpen(true);
  };

  const applyRoles = () => {
    setFilters((current) => ({ ...current, roles: roleDraft }));
    setPage(0);
    setRoleMenuOpen(false);
  };

  const clearRoles = () => {
    setFilters((current) => ({ ...current, roles: [] }));
    setRoleDraft([]);
    setPage(0);
  };

  const postings = result?.content ?? [];
  const first = result && result.totalElements > 0 ? page * result.size + 1 : 0;
  const last = result ? Math.min((page + 1) * result.size, result.totalElements) : 0;

  return <>
    <PageHeading eyebrow="DEVELOPER JOBS" title="전체 채용공고" body="개발 직무 공고를 직무·경력·지역 조건으로 찾아보세요." />

    <form className="posting-search" onSubmit={submit}>
      <Search size={17} />
      <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="회사명, 공고명, 기술, 지역으로 검색" />
      <button type="submit">검색</button>
    </form>

    <section className="posting-filters" aria-label="채용공고 필터">
      <div className="role-filter">
        <button type="button" className="filter-button" onClick={openRoleMenu} aria-expanded={roleMenuOpen}>
          <span>{filters.roles.length === 0 ? "직무 전체" : `직무 ${filters.roles.length}개`}</span><ChevronDown size={15} />
        </button>
        {roleMenuOpen && <div className="role-filter-menu">
          <div className="role-filter-head"><strong>개발 직무</strong><button type="button" onClick={() => setRoleMenuOpen(false)} aria-label="직무 필터 닫기"><X size={17} /></button></div>
          <p>여러 직무를 함께 선택할 수 있어요.</p>
          <div className="role-check-list">{roleOptions.map(([key, label]) => <label key={key}><input type="checkbox" checked={roleDraft.includes(key)} onChange={() => toggleDraftRole(key)} />{label}</label>)}</div>
          <div className="role-filter-actions"><button type="button" onClick={() => setRoleDraft([])}>초기화</button><button type="button" className="primary-button" onClick={applyRoles}>적용</button></div>
        </div>}
      </div>

      <label className="filter-select"><span className="sr-only">경력</span><select value={filters.experience} onChange={(event) => updateFilter("experience", event.target.value as JobExperienceFilter)}><option value="">경력 전체</option><option value="ENTRY">신입 가능</option><option value="EXPERIENCED">경력</option></select><ChevronDown size={15} /></label>
      <label className="filter-select"><span className="sr-only">지역</span><select value={filters.location} onChange={(event) => updateFilter("location", event.target.value)}><option value="">지역 전체</option><option value="서울">서울</option><option value="경기">경기</option><option value="인천">인천</option><option value="부산">부산</option><option value="대전">대전</option><option value="대구">대구</option><option value="광주">광주</option><option value="울산">울산</option><option value="세종">세종</option><option value="제주">제주</option></select><ChevronDown size={15} /></label>
      <label className="filter-select"><span className="sr-only">고용 형태</span><select value={filters.employmentType} onChange={(event) => updateFilter("employmentType", event.target.value)}><option value="">고용 형태 전체</option><option value="regular">정규직</option><option value="contract">계약직</option><option value="intern">인턴</option></select><ChevronDown size={15} /></label>
      <label className="filter-select sort-select"><span className="sr-only">정렬</span><select value={filters.sort} onChange={(event) => updateFilter("sort", event.target.value as JobPostingSort)}><option value="deadline_asc">마감 임박순</option><option value="deadline_desc">마감일 늦은순</option><option value="recent">최근 수집순</option></select><ChevronDown size={15} /></label>
    </section>

    {filters.roles.length > 0 && <div className="selected-filter-chips">{filters.roles.map((role) => <button key={role} type="button" onClick={() => { setFilters((current) => ({ ...current, roles: current.roles.filter((item) => item !== role) })); setPage(0); }}>{roleName(role)}<X size={13} /></button>)}<button type="button" className="clear-filter-chip" onClick={clearRoles}>직무 초기화</button></div>}

    {status === "loading" && <DataStatePanel state="loading" />}
    {status === "error" && <DataStatePanel state="error" />}
    {status === "ready" && postings.length === 0 && <DataStatePanel state="empty" emptyTitle="조건에 맞는 채용공고가 없습니다" emptyBody="직무·경력·지역 조건을 조금 넓혀서 다시 찾아보세요." />}
    {status === "ready" && postings.length > 0 && <>
      <div className="list-heading"><strong>총 {result?.totalElements.toLocaleString()}개 중 {first.toLocaleString()}–{last.toLocaleString()}</strong><span>{sortLabel[filters.sort]}</span></div>
      <section className="posting-grid">{postings.map((posting) => <JobPostingCard key={posting.id} posting={posting} />)}</section>
      {pages.length > 0 && <nav className="posting-pagination" aria-label="채용공고 페이지"><button type="button" disabled={page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))}>이전</button>{pages.map((item) => <button key={item} type="button" className={item === page ? "active" : ""} onClick={() => setPage(item)} aria-current={item === page ? "page" : undefined}>{item + 1}</button>)}<button type="button" disabled={page + 1 >= (result?.totalPages ?? 0)} onClick={() => setPage((current) => current + 1)}>다음</button></nav>}
    </>}
  </>;
}

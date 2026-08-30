import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, Landmark, Search, X } from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { getJobPostings } from "../features/job-postings/api/jobPostingsApi";
import { JobPostingCard } from "../features/job-postings/components/JobPostingCard";
import type { JobExperienceFilter, JobPostingPage, JobPostingSearchParams, JobPostingSort } from "../features/job-postings/model/jobPosting.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";
import { StyledSelect } from "../shared/components/StyledSelect";

const PAGE_SIZE = 24;

const roleOptions = [
  ["BACKEND", "백엔드"], ["FRONTEND", "프론트엔드"], ["FULLSTACK", "풀스택"],
  ["MOBILE", "모바일"], ["DATA_AI", "데이터 · AI"], ["DEVOPS", "DevOps · 인프라"],
  ["QA", "QA · 테스트"], ["SECURITY", "보안"], ["GAME_EMBEDDED", "게임 · 임베디드"],
] as const;

const roleName = (value: string) => roleOptions.find(([key]) => key === value)?.[1] ?? value;
const experienceOptions = [{ value: "", label: "경력 전체" }, { value: "ENTRY", label: "신입 가능" }, { value: "EXPERIENCED", label: "경력" }] as const;
const locationOptions = ["", "서울", "경기", "인천", "부산", "대전", "대구", "광주", "울산", "세종", "제주"].map((value) => ({ value, label: value || "지역 전체" }));
const employmentOptions = [{ value: "", label: "고용 형태 전체" }, { value: "regular", label: "정규직" }, { value: "contract", label: "계약직" }, { value: "intern", label: "인턴" }] as const;
const sortOptions: readonly { value: JobPostingSort; label: string }[] = [{ value: "deadline_asc", label: "마감 임박순" }, { value: "popular", label: "인기순 (조회·찜)" }, { value: "deadline_desc", label: "마감일 늦은순" }, { value: "recent", label: "최근 수집순" }];
const sortLabel: Record<JobPostingSort, string> = {
  deadline_asc: "마감 임박순",
  deadline_desc: "마감일 늦은순",
  recent: "최근 수집순",
  popular: "인기순",
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

function isSort(value: string | null): value is JobPostingSort {
  return value === "deadline_asc" || value === "deadline_desc" || value === "recent" || value === "popular";
}

function sortFromParams(value: string | null): JobPostingSort {
  return isSort(value) ? value : "deadline_asc";
}

export function AllJobPostingsPage() {
  const [searchParams] = useSearchParams();
  const initialQuery = searchParams.get("query")?.trim() ?? "";
  const initialSort = sortFromParams(searchParams.get("sort"));
  const [query, setQuery] = useState(initialQuery);
  const [filters, setFilters] = useState<Filters>({ ...initialFilters, query: initialQuery, sort: initialSort });
  const [roleDraft, setRoleDraft] = useState<string[]>([]);
  const [openFilter, setOpenFilter] = useState<"role" | "experience" | "location" | "employment" | "sort" | null>(null);
  const roleFilterRef = useRef<HTMLDivElement>(null);
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

  useEffect(() => {
    const nextQuery = searchParams.get("query")?.trim() ?? "";
    const nextSort = sortFromParams(searchParams.get("sort"));
    setQuery(nextQuery);
    setFilters((current) => current.query === nextQuery && current.sort === nextSort ? current : { ...current, query: nextQuery, sort: nextSort });
    setPage(0);
  }, [searchParams]);

  useEffect(() => {
    const closeOutside = (event: PointerEvent) => {
      if (openFilter === "role" && !roleFilterRef.current?.contains(event.target as Node)) setOpenFilter(null);
    };
    const closeEscape = (event: KeyboardEvent) => { if (event.key === "Escape") setOpenFilter(null); };
    document.addEventListener("pointerdown", closeOutside);
    document.addEventListener("keydown", closeEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOutside);
      document.removeEventListener("keydown", closeEscape);
    };
  }, [openFilter]);

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
    setOpenFilter((current) => current === "role" ? null : "role");
  };

  const applyRoles = () => {
    setFilters((current) => ({ ...current, roles: roleDraft }));
    setPage(0);
    setOpenFilter(null);
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
      <div className="role-filter" ref={roleFilterRef}>
        <button type="button" className="filter-button" onClick={openRoleMenu} aria-expanded={openFilter === "role"}>
          <span>{filters.roles.length === 0 ? "직무 전체" : `직무 ${filters.roles.length}개`}</span><ChevronDown size={15} />
        </button>
        {openFilter === "role" && <div className="role-filter-menu">
          <div className="role-filter-head"><strong>개발 직무</strong><button type="button" onClick={() => setOpenFilter(null)} aria-label="직무 필터 닫기"><X size={17} /></button></div>
          <p>여러 직무를 함께 선택할 수 있어요.</p>
          <div className="role-check-list">{roleOptions.map(([key, label]) => <label key={key}><input type="checkbox" checked={roleDraft.includes(key)} onChange={() => toggleDraftRole(key)} />{label}</label>)}</div>
          <div className="role-filter-actions"><button type="button" onClick={() => setRoleDraft([])}>초기화</button><button type="button" className="primary-button" onClick={applyRoles}>적용</button></div>
        </div>}
      </div>

      <StyledSelect label="경력" open={openFilter === "experience"} onOpenChange={(open) => setOpenFilter(open ? "experience" : null)} value={filters.experience} options={experienceOptions} onChange={(value) => updateFilter("experience", value)} />
      <StyledSelect label="지역" open={openFilter === "location"} onOpenChange={(open) => setOpenFilter(open ? "location" : null)} value={filters.location} options={locationOptions} onChange={(value) => updateFilter("location", value)} />
      <StyledSelect label="고용 형태" open={openFilter === "employment"} onOpenChange={(open) => setOpenFilter(open ? "employment" : null)} value={filters.employmentType} options={employmentOptions} onChange={(value) => updateFilter("employmentType", value)} />
      <StyledSelect label="정렬" className="sort-select" open={openFilter === "sort"} onOpenChange={(open) => setOpenFilter(open ? "sort" : null)} value={filters.sort} options={sortOptions} onChange={(value) => updateFilter("sort", value)} />
    </section>

    {filters.roles.length > 0 && <div className="selected-filter-chips">{filters.roles.map((role) => <button key={role} type="button" onClick={() => { setFilters((current) => ({ ...current, roles: current.roles.filter((item) => item !== role) })); setPage(0); }}>{roleName(role)}<X size={13} /></button>)}<button type="button" className="clear-filter-chip" onClick={clearRoles}>직무 초기화</button></div>}

    {status === "loading" && <DataStatePanel state="loading" />}
    {status === "error" && <DataStatePanel state="error" />}
    {status === "ready" && postings.length === 0 && <DataStatePanel state="empty" emptyTitle="조건에 맞는 채용공고가 없습니다" emptyBody="직무·경력·지역 조건을 조금 넓혀서 다시 찾아보세요." />}
    {status === "ready" && postings.length > 0 && <>
      <div className="list-heading"><strong>총 {result?.totalElements.toLocaleString()}개 중 {first.toLocaleString()}–{last.toLocaleString()}</strong><div className="posting-list-guides"><span className="finance-guide"><Landmark size={12} />카드에서 재무 연결 상태 확인</span><span>{sortLabel[filters.sort]}</span></div></div>
      <section className="posting-grid">{postings.map((posting) => <JobPostingCard key={posting.id} posting={posting} />)}</section>
      {pages.length > 0 && <nav className="posting-pagination" aria-label="채용공고 페이지"><button type="button" disabled={page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))}>이전</button>{pages.map((item) => <button key={item} type="button" className={item === page ? "active" : ""} onClick={() => setPage(item)} aria-current={item === page ? "page" : undefined}>{item + 1}</button>)}<button type="button" disabled={page + 1 >= (result?.totalPages ?? 0)} onClick={() => setPage((current) => current + 1)}>다음</button></nav>}
    </>}
  </>;
}

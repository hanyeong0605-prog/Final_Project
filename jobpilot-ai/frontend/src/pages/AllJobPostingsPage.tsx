import { FormEvent, useEffect, useMemo, useState } from "react";
import { ChevronDown, Landmark, Search, X } from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { getJobPostings } from "../features/job-postings/api/jobPostingsApi";
import { JobPostingCard } from "../features/job-postings/components/JobPostingCard";
import type { JobExperienceFilter, JobPostingPage, JobPostingSearchParams, JobPostingSort } from "../features/job-postings/model/jobPosting.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";

const PAGE_SIZE = 24;

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

function FilterSelect<Value extends string>({ ariaLabel, value, options, onChange }: { ariaLabel: string; value: Value; options: readonly { value: Value; label: string }[]; onChange: (value: Value) => void }) {
  return <label className="filter-select"><select aria-label={ariaLabel} value={value} onChange={(event) => onChange(event.target.value as Value)}>{options.map((option) => <option key={option.value || "all"} value={option.value}>{option.label}</option>)}</select><ChevronDown size={15} /></label>;
}

type Filters = {
  query: string;
  experience: JobExperienceFilter;
  location: string;
  employmentType: string;
  financialsOnly: boolean;
  sort: JobPostingSort;
};

const initialFilters: Filters = {
  query: "", experience: "", location: "", employmentType: "", financialsOnly: false, sort: "deadline_asc",
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
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<JobPostingPage | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let active = true;
    setStatus("loading");
    const params: JobPostingSearchParams = { ...filters, page, size: PAGE_SIZE };
    void getJobPostings(params)
      .then((data) => { if (active) { setResult(data); setStatus("ready"); } })
      .catch(() => { if (active) { setResult(null); setStatus("error"); } });
    return () => { active = false; };
  }, [filters, page, reloadToken]);

  useEffect(() => {
    const nextQuery = searchParams.get("query")?.trim() ?? "";
    const nextSort = sortFromParams(searchParams.get("sort"));
    setQuery(nextQuery);
    setFilters((current) => current.query === nextQuery && current.sort === nextSort ? current : { ...current, query: nextQuery, sort: nextSort });
    setPage(0);
  }, [searchParams]);

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

  const updateFilter = <Key extends keyof Omit<Filters, "query">>(key: Key, value: Filters[Key]) => {
    setFilters((current) => ({ ...current, [key]: value }));
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
      <FilterSelect ariaLabel="경력" value={filters.experience} options={experienceOptions} onChange={(value) => updateFilter("experience", value)} />
      <FilterSelect ariaLabel="지역" value={filters.location} options={locationOptions} onChange={(value) => updateFilter("location", value)} />
      <FilterSelect ariaLabel="고용 형태" value={filters.employmentType} options={employmentOptions} onChange={(value) => updateFilter("employmentType", value)} />
      <span className="sort-select"><FilterSelect ariaLabel="정렬" value={filters.sort} options={sortOptions} onChange={(value) => updateFilter("sort", value)} /></span>
      <label className="finance-filter-switch">
        <input type="checkbox" checked={filters.financialsOnly} onChange={(event) => updateFilter("financialsOnly", event.target.checked)} />
        <span aria-hidden="true"><i /></span>
        <Landmark size={14} />재무제표 있는 공고만
      </label>
    </section>

    {(filters.experience || filters.location || filters.employmentType || filters.financialsOnly) && <div className="selected-filter-chips">{filters.experience && <button type="button" onClick={() => updateFilter("experience", "")}>{experienceOptions.find((option) => option.value === filters.experience)?.label}<X size={13} /></button>}{filters.location && <button type="button" onClick={() => updateFilter("location", "")}>{filters.location}<X size={13} /></button>}{filters.employmentType && <button type="button" onClick={() => updateFilter("employmentType", "")}>{employmentOptions.find((option) => option.value === filters.employmentType)?.label}<X size={13} /></button>}{filters.financialsOnly && <button type="button" onClick={() => updateFilter("financialsOnly", false)}>재무제표 있는 공고<X size={13} /></button>}<button type="button" className="clear-filter-chip" onClick={() => { setFilters((current) => ({ ...current, experience: "", location: "", employmentType: "", financialsOnly: false })); setPage(0); }}>필터 초기화</button></div>}

    {status === "loading" && <DataStatePanel state="loading" />}
    {status === "error" && <DataStatePanel state="error" errorTitle="채용공고 목록을 불러오지 못했습니다" errorBody="공고는 일시적으로 갱신될 수 있습니다. 잠시 후 다시 불러오거나 홈에서 최근 공고를 확인해 주세요." onRetry={() => setReloadToken((value) => value + 1)} />}
    {status === "ready" && postings.length === 0 && <DataStatePanel state="empty" emptyTitle="조건에 맞는 채용공고가 없습니다" emptyBody="직무·경력·지역 조건을 조금 넓혀서 다시 찾아보세요." />}
    {status === "ready" && postings.length > 0 && <>
      <div className="list-heading"><strong>총 {result?.totalElements.toLocaleString()}개 중 {first.toLocaleString()}–{last.toLocaleString()}</strong><div className="posting-list-guides"><span className="finance-guide"><Landmark size={12} />카드에서 재무 연결 상태 확인</span><span>{sortLabel[filters.sort]}</span></div></div>
      <section className="posting-grid">{postings.map((posting) => <JobPostingCard key={posting.id} posting={posting} />)}</section>
      {pages.length > 0 && <nav className="posting-pagination" aria-label="채용공고 페이지"><button type="button" disabled={page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))}>이전</button>{pages.map((item) => <button key={item} type="button" className={item === page ? "active" : ""} onClick={() => setPage(item)} aria-current={item === page ? "page" : undefined}>{item + 1}</button>)}<button type="button" disabled={page + 1 >= (result?.totalPages ?? 0)} onClick={() => setPage((current) => current + 1)}>다음</button></nav>}
    </>}
  </>;
}

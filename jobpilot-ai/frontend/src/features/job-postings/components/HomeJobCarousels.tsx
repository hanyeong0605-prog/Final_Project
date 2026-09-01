import { ChevronLeft, ChevronRight, Eye, Heart, MapPin, RotateCw } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getJobPostings } from "../api/jobPostingsApi";
import type { JobPosting, JobPostingSort } from "../model/jobPosting.types";

type ShowcaseProps = { eyebrow: string; title: string; description: string; sort: JobPostingSort; moreTo: string };
const CARDS_PER_PAGE = 4;
const MAX_SHOWCASE_POSTINGS = 28;

function cacheKey(sort: JobPostingSort) { return `jobpilot.home-showcase.${sort}`; }

function cachedPostings(sort: JobPostingSort): JobPosting[] {
  try {
    const stored = sessionStorage.getItem(cacheKey(sort));
    const parsed = stored ? JSON.parse(stored) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveCachedPostings(sort: JobPostingSort, postings: JobPosting[]) {
  try { sessionStorage.setItem(cacheKey(sort), JSON.stringify(postings)); } catch { /* storage is optional */ }
}

function HomeJobShowcase({ eyebrow, title, description, sort, moreTo }: ShowcaseProps) {
  const [postings, setPostings] = useState<JobPosting[]>(() => cachedPostings(sort));
  const [page, setPage] = useState(0);
  const [loadError, setLoadError] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);
  const maxPage = Math.max(0, Math.ceil(postings.length / CARDS_PER_PAGE) - 1);
  const safePage = Math.min(page, maxPage);
  const visible = postings.slice(safePage * CARDS_PER_PAGE, safePage * CARDS_PER_PAGE + CARDS_PER_PAGE);
  const progress = postings.length <= CARDS_PER_PAGE ? 100 : ((safePage + 1) / (maxPage + 1)) * 100;

  useEffect(() => {
    let active = true;
    const load = async () => {
      setLoadError(false);
      try {
        // A short retry absorbs a container restart without replacing home cards with an error.
        let result: Awaited<ReturnType<typeof getJobPostings>>;
        try { result = await getJobPostings({ sort, page: 0, size: MAX_SHOWCASE_POSTINGS }); }
        catch { await new Promise((resolve) => window.setTimeout(resolve, 700)); result = await getJobPostings({ sort, page: 0, size: MAX_SHOWCASE_POSTINGS }); }
        if (!active) return;
        setPostings(result.content);
        saveCachedPostings(sort, result.content);
        setPage(0);
      } catch {
        if (active) setLoadError(true);
      }
    };
    void load();
    return () => { active = false; };
  }, [sort, reloadToken]);

  const hasPopularitySignal = sort !== "popular" || postings.some((posting) => (posting.viewCount ?? 0) > 0 || (posting.bookmarkCount ?? 0) > 0);
  return <section className="home-job-showcase" data-scroll-reveal>
    <div className="home-section-heading"><div><span className="eyebrow">{eyebrow}</span><h2>{hasPopularitySignal ? title : "인기 데이터 집계 중"}</h2><p className="home-section-description">{hasPopularitySignal ? description : "회원들의 조회와 찜이 쌓이면 실제 인기 채용공고를 보여드려요."}</p></div>{hasPopularitySignal && <Link to={moreTo}>전체 보기 <ChevronRight size={15} /></Link>}</div>
    <div className="home-job-rail">
      <button className="home-showcase-arrow previous" type="button" disabled={safePage === 0} onClick={() => setPage((value) => Math.max(0, value - 1))} aria-label="이전 공고 4개"><ChevronLeft size={29} /></button>
      <div className="home-job-carousel">
        {hasPopularitySignal && visible.map((posting) => <HomeJobCard posting={posting} key={posting.id} />)}
        {!hasPopularitySignal && <div className="home-job-empty">공고 상세를 조회하거나 찜하면 인기 공고 순위가 자동으로 만들어집니다.</div>}
        {hasPopularitySignal && visible.length === 0 && <div className="home-job-empty">{loadError ? <><span>공고를 불러오지 못했습니다.</span><button type="button" onClick={() => setReloadToken((value) => value + 1)}><RotateCw size={14} /> 다시 불러오기</button></> : "표시할 채용공고가 없습니다."}</div>}
      </div>
      <button className="home-showcase-arrow next" type="button" disabled={safePage >= maxPage} onClick={() => setPage((value) => Math.min(maxPage, value + 1))} aria-label="다음 공고 4개"><ChevronRight size={29} /></button>
    </div>
    {hasPopularitySignal && postings.length > 0 && <div className="home-showcase-controls"><div className="home-showcase-pagination" aria-label={`${safePage + 1}페이지 / 총 ${maxPage + 1}페이지`}><b>{safePage + 1}</b><i aria-hidden="true"><em style={{ width: `${progress}%` }} /></i><span>{maxPage + 1}</span></div></div>}
  </section>;
}

function HomeJobCard({ posting }: { posting: JobPosting }) {
  const [imageFailed, setImageFailed] = useState(false);
  const [logoFailed, setLogoFailed] = useState(false);
  const preview = posting.thumbnailUrl;
  const companyLogo = posting.companyLogoUrl;
  return <Link to={`/job-postings/${posting.id}`} className="home-job-card">
    <div className="home-job-preview">{preview && !imageFailed ? <img src={preview} alt={`${posting.companyName ?? "채용 기업"} 공고 미리보기`} onError={() => setImageFailed(true)} /> : null}</div>
    <div className="home-job-company">{companyLogo && !logoFailed ? <img src={companyLogo} alt="" onError={() => setLogoFailed(true)} /> : <span>{posting.companyName?.slice(0, 1) ?? "J"}</span>}<small>{posting.companyName ?? "채용 기업"}</small></div>
    <strong>{posting.title}</strong><p>{posting.location && <><MapPin size={13} />{posting.location}</>}</p>
    <footer><span><Eye size={13} /> {(posting.viewCount ?? 0).toLocaleString()}</span><span><Heart size={13} /> {(posting.bookmarkCount ?? 0).toLocaleString()}</span></footer>
  </Link>;
}

export function HomeJobCarousels() {
  return <div className="home-job-showcases">
    <HomeJobShowcase eyebrow="POPULAR JOBS" title="지금 많이 보는 인기 채용공고" description="회원들의 조회와 찜이 많이 쌓인 공고를 먼저 확인해 보세요." sort="popular" moreTo="/job-postings?sort=popular" />
    <HomeJobShowcase eyebrow="DEADLINE SOON" title="놓치기 쉬운 마감 임박 채용공고" description="지원 기회를 놓치지 않도록 마감이 가까운 공고를 모았습니다." sort="deadline_asc" moreTo="/job-postings?sort=deadline_asc" />
  </div>;
}

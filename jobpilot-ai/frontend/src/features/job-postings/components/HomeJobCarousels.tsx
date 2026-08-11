import { ChevronLeft, ChevronRight, Eye, Heart, MapPin } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getJobPostings } from "../api/jobPostingsApi";
import type { JobPosting, JobPostingSort } from "../model/jobPosting.types";

type ShowcaseProps = { eyebrow: string; title: string; description: string; sort: JobPostingSort; moreTo: string };

function HomeJobShowcase({ eyebrow, title, description, sort, moreTo }: ShowcaseProps) {
  const [postings, setPostings] = useState<JobPosting[]>([]);
  const [page, setPage] = useState(0);
  const pageSize = 3;
  const maxPage = Math.max(0, Math.ceil(postings.length / pageSize) - 1);

  useEffect(() => {
    let active = true;
    void getJobPostings({ sort, page: 0, size: 20 }).then((result) => {
      if (active) setPostings(result.content);
    }).catch(() => { if (active) setPostings([]); });
    return () => { active = false; };
  }, [sort]);

  const visible = postings.slice(page * pageSize, page * pageSize + pageSize);
  const hasPopularitySignal = sort !== "popular" || postings.some((posting) => (posting.viewCount ?? 0) > 0 || (posting.bookmarkCount ?? 0) > 0);
  return <section className="home-job-showcase" data-scroll-reveal>
    <div className="home-section-heading"><div><span className="eyebrow">{eyebrow}</span><h2>{hasPopularitySignal ? title : "인기 데이터 집계 중"}</h2><p className="home-section-description">{hasPopularitySignal ? description : "회원들의 조회와 찜이 쌓이면 실제 인기 채용공고를 보여드려요."}</p></div>{hasPopularitySignal && <Link to={moreTo}>전체 보기 <ChevronRight size={15} /></Link>}</div>
    <div className="home-job-carousel">
      {hasPopularitySignal && visible.map((posting) => <Link to={`/job-postings/${posting.id}`} className="home-job-card" key={posting.id}>
        <div className="home-job-company">{posting.thumbnailUrl || posting.companyLogoUrl ? <img src={posting.thumbnailUrl || posting.companyLogoUrl || undefined} alt="" /> : <span>{posting.companyName?.slice(0, 1) ?? "J"}</span>}<small>{posting.companyName ?? "채용 기업"}</small></div>
        <strong>{posting.title}</strong><p>{posting.location && <><MapPin size={13} />{posting.location}</>}</p>
        <footer><span><Eye size={13} /> {(posting.viewCount ?? 0).toLocaleString()}</span><span><Heart size={13} /> {(posting.bookmarkCount ?? 0).toLocaleString()}</span></footer>
      </Link>)}
      {!hasPopularitySignal && <div className="home-job-empty">공고 상세를 조회하거나 찜하면 인기 공고 순위가 자동으로 만들어집니다.</div>}
      {hasPopularitySignal && visible.length === 0 && <div className="home-job-empty">표시할 채용공고가 없습니다.</div>}
    </div>
    {hasPopularitySignal && <div className="home-showcase-controls"><button type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)} aria-label="이전 공고"><ChevronLeft size={21} /></button><div className="home-showcase-pagination" aria-label={`${page + 1}번째 묶음 / 총 ${maxPage + 1}묶음`}><b>{String(page + 1).padStart(2, "0")}</b><i /><span>{String(maxPage + 1).padStart(2, "0")}</span></div><button type="button" disabled={page >= maxPage} onClick={() => setPage((value) => value + 1)} aria-label="다음 공고"><ChevronRight size={21} /></button></div>}
  </section>;
}

export function HomeJobCarousels() {
  return <div className="home-job-showcases">
    <HomeJobShowcase eyebrow="POPULAR JOBS" title="지금 많이 보는 인기 채용공고" description="회원들의 조회와 찜이 많이 쌓인 공고를 먼저 확인해 보세요." sort="popular" moreTo="/job-postings?sort=popular" />
    <HomeJobShowcase eyebrow="DEADLINE SOON" title="놓치기 쉬운 마감 임박 채용공고" description="지원 기회를 놓치지 않도록 마감이 가까운 공고를 모았습니다." sort="deadline_asc" moreTo="/job-postings?sort=deadline_asc" />
  </div>;
}

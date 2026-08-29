import { useEffect, useState } from "react";
import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import { getJson } from "../../../api/httpClient";

type Rank = { id:number; jobPostingId:number; name:string; title?:string; reviewCount:number; averageRating:number; positiveSentiment?:number; score:number };

export function HomeReviewRankings() {
  const [tab, setTab] = useState<"companies"|"postings">("companies");
  const [rows, setRows] = useState<Rank[]>([]);
  useEffect(() => { getJson<Rank[]>(`/api/v1/review-companies/rankings/${tab}`).then(setRows).catch(() => setRows([])); }, [tab]);
  return <section className="panel home-review-ranking" data-scroll-reveal>
    <div className="home-section-heading"><div><span className="eyebrow">REVIEW INSIGHT</span><h2>근무 평가가 좋은 {tab === "companies" ? "회사" : "채용공고"} TOP 10</h2></div><Link to="/job-postings">전체 채용공고 <ChevronRight size={14}/></Link></div>
    <div className="ranking-tabs"><button className={tab === "companies" ? "active" : ""} onClick={() => setTab("companies")}>기업</button><button className={tab === "postings" ? "active" : ""} onClick={() => setTab("postings")}>공고</button></div>
    {rows.length ? <ol>{rows.map((r,i) => <li key={r.id}><Link to={`/job-postings/${r.jobPostingId}`}><b>{i+1}</b><span><strong>{r.title || r.name}</strong><small>{r.title && `${r.name} · `}리뷰 {r.reviewCount}개 · 평균 {r.averageRating.toFixed(1)}점</small></span><em>{r.score.toFixed(3)}</em></Link></li>)}</ol> : <p className="data-empty">분석 완료 후 순위가 표시됩니다.</p>}
    <small>포트폴리오 가상공고의 별점, 종합 감정, 표본 수와 최신성을 함께 반영한 참고 순위입니다.</small>
  </section>;
}

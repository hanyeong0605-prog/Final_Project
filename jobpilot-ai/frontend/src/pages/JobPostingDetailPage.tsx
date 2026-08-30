import { useEffect, useState } from "react";
import { ArrowLeft, BadgeCheck, Bookmark, BriefcaseBusiness, Building2, CalendarDays, Eye, ExternalLink, Landmark, MapPin, Star, Target } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { getJobPosting } from "../features/job-postings/api/jobPostingsApi";
import type { JobPostingDetail } from "../features/job-postings/model/jobPosting.types";
import { refreshJobMatchEvidence } from "../features/jobs/api/jobMatchesApi";
import { JobMatchDrawer } from "../features/jobs/components/JobMatchDrawer";
import type { JobMatch } from "../features/jobs/model/job.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { useInterests } from "../features/interests/model/InterestContext";
import { CompanyFinanceSection } from "../features/company-finance/components/CompanyFinanceSection";
import { getJson, postJson } from "../api/httpClient";

interface JobReview { id: number; rating: number; content: string; employmentVerified: boolean; mine: boolean; createdAt: string; }

function hasText(value: string | null | undefined): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function dateLabel(value: string) {
  return new Intl.DateTimeFormat("ko-KR").format(new Date(value));
}

function deadlineLabel(posting: JobPostingDetail): string | null {
  if (posting.rollingDeadline) return "상시 채용";
  return hasText(posting.deadlineAt) ? dateLabel(posting.deadlineAt) : null;
}

export function JobPostingDetailPage() {
  const { id } = useParams();
  const [posting, setPosting] = useState<JobPostingDetail | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [companyImageFailed, setCompanyImageFailed] = useState(false);
  const [selectedMatch, setSelectedMatch] = useState<JobMatch | null>(null);
  const [matching, setMatching] = useState(false);
  const [matchingError, setMatchingError] = useState<string | null>(null);
  const { isInterested, toggleInterest } = useInterests();
  const [reviews, setReviews] = useState<JobReview[]>([]);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewContent, setReviewContent] = useState("");
  const [reviewMessage, setReviewMessage] = useState("");

  useEffect(() => {
    if (!id) { setStatus("error"); return; }
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 12_000);
    let active = true;
    setStatus("loading");
    void getJobPosting(id, { signal: controller.signal })
      .then((data) => { if (active) { setPosting(data); setCompanyImageFailed(false); setStatus("ready"); } })
      .catch(() => { if (active) { setPosting(null); setStatus("error"); } })
      .finally(() => window.clearTimeout(timeout));
    return () => { active = false; controller.abort(); window.clearTimeout(timeout); };
  }, [id]);

  useEffect(() => {
    if (!id) return;
    void getJson<JobReview[]>(`/api/v1/job-postings/${id}/reviews`).then((items) => {
      setReviews(items);
      const mine = items.find((item) => item.mine);
      if (mine) { setReviewRating(mine.rating); setReviewContent(mine.content); }
    }).catch(() => setReviews([]));
  }, [id]);

  if (status === "loading") return <DataStatePanel state="loading" />;
  if (status === "error" || !posting) {
    return <><Link className="job-detail-back" to="/job-postings"><ArrowLeft size={16} />전체 채용공고</Link><DataStatePanel state="error" /></>;
  }

  const locations = posting.locations ?? [];
  const primaryLocation = locations.find((location) => location.primaryLocation) ?? locations[0] ?? null;
  const locationText = primaryLocation?.locationText ?? posting.location;
  const roleName = posting.jobName ?? posting.jobMidName;
  const deadline = deadlineLabel(posting);
  const workType = [posting.experienceType, posting.employmentType].filter(hasText).join(" · ");
  const details: Array<[string, string]> = [
    ...(hasText(locationText) ? [["근무지", locationText] as [string, string]] : []),
    ...(hasText(primaryLocation?.detailedAddress) ? [["상세 주소", primaryLocation.detailedAddress] as [string, string]] : []),
    ...(hasText(posting.experienceType) ? [["경력", posting.experienceType] as [string, string]] : []),
    ...(hasText(posting.employmentType) ? [["고용 형태", posting.employmentType] as [string, string]] : []),
    ...(hasText(roleName) ? [["직무", roleName] as [string, string]] : []),
    ...(hasText(posting.industryName) ? [["산업", posting.industryName] as [string, string]] : []),
    ...(hasText(posting.salary) ? [["급여", posting.salary] as [string, string]] : []),
    ...(hasText(posting.publishedAt) ? [["게시일", dateLabel(posting.publishedAt)] as [string, string]] : []),
    ...(deadline ? [["마감일", deadline] as [string, string]] : []),
  ];
  const images = [...new Set(posting.imageUrls ?? [])].slice(0, 8);
  const companyImageUrl = posting.thumbnailUrl || posting.companyLogoUrl;
  const interested = isInterested(posting.id);

  const openMatch = async () => {
    setMatching(true);
    setMatchingError(null);
    try {
      // Rebuild this one posting first, so the drawer always reflects the latest profile.
      setSelectedMatch(await refreshJobMatchEvidence(posting.id));
    } catch (error) {
      setMatchingError(error instanceof Error ? error.message : "역량 매칭 결과를 불러오지 못했습니다.");
    } finally {
      setMatching(false);
    }
  };
  const saveReview = async () => {
    setReviewMessage("");
    try {
      const saved = await postJson<JobReview>(`/api/v1/job-postings/${posting.id}/reviews`, { rating: reviewRating, content: reviewContent });
      setReviews((current) => [saved, ...current.filter((item) => item.id !== saved.id)]);
      setReviewMessage(saved.employmentVerified ? "재직 이력 인증 리뷰로 저장했습니다." : "리뷰를 저장했습니다. 이력서 경력의 회사명이 일치하면 재직 인증 배지가 표시됩니다.");
    } catch (error) { setReviewMessage(error instanceof Error ? error.message : "리뷰 저장에 실패했습니다."); }
  };

  return <div className="job-detail-page">
    <Link className="job-detail-back" to="/job-postings"><ArrowLeft size={16} />전체 채용공고</Link>
    <section className="job-detail-hero">
      <div className="job-detail-company">
        {hasText(companyImageUrl) && !companyImageFailed
          ? <img src={companyImageUrl} alt={`${posting.companyName ?? "회사"} 로고`} onError={() => setCompanyImageFailed(true)} />
          : <Building2 size={30} />}
        {hasText(posting.companyName) && <span>{posting.companyName}</span>}
      </div>
      <h1>{posting.title}</h1>
      {(hasText(locationText) || workType || deadline) && <div className="job-detail-meta">
        {hasText(locationText) && <span><MapPin size={16} />{locationText}</span>}
        {workType && <span><BriefcaseBusiness size={16} />{workType}</span>}
        {deadline && <span><CalendarDays size={16} />마감 {deadline}</span>}
      </div>}
      <div className="job-detail-actions">
        <a className="primary-button job-source-link" href={posting.sourceUrl} target="_blank" rel="noreferrer">공고 원문 보기<ExternalLink size={15} /></a>
        <a className="outline-button job-detail-finance" href="#company-finance" onClick={(event) => { event.preventDefault(); document.getElementById("company-finance")?.scrollIntoView({ behavior: "smooth", block: "start" }); }}><Landmark size={15} />기업 재무 분석</a>
        <button className="outline-button job-detail-match" type="button" onClick={() => void openMatch()} disabled={matching}><Target size={15} />{matching ? "매칭 분석 중..." : "내 역량과 매칭"}</button>
        <button className={interested ? "outline-button job-detail-bookmark active" : "outline-button job-detail-bookmark"} type="button" onClick={() => void toggleInterest(posting.id)}><Bookmark size={15} fill={interested ? "currentColor" : "none"} />{interested ? "찜한 공고" : "공고 찜하기"}</button>
      </div>
      {matchingError && <p className="job-detail-match-error" role="alert">{matchingError}</p>}
      <div className="job-detail-stats"><span><Eye size={15} />조회 {posting.viewCount?.toLocaleString() ?? 0}</span><span><Bookmark size={14} />찜 {posting.bookmarkCount?.toLocaleString() ?? 0}</span></div>
    </section>

    {images.length > 0 && <section className="job-detail-section"><div className="job-detail-section-heading"><span className="eyebrow">COMPANY IMAGES</span><h2>공고 이미지</h2></div><div className="job-image-gallery">{images.map((url) => <img key={url} src={url} alt={`${posting.companyName ?? "회사"} 공고 이미지`} loading="lazy" onError={(event) => { event.currentTarget.style.display = "none"; }} />)}</div></section>}

    {(hasText(posting.description) || details.length > 0) && <div className="job-detail-layout">
      {hasText(posting.description) && <section className="job-detail-section job-description"><div className="job-detail-section-heading"><span className="eyebrow">JOB DESCRIPTION</span><h2>공고 상세</h2></div><p>{posting.description}</p></section>}
      {details.length > 0 && <aside className="job-detail-section job-information"><div className="job-detail-section-heading"><span className="eyebrow">JOB INFORMATION</span><h2>공고 정보</h2></div><dl>{details.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl></aside>}
    </div>}
    <section className="job-detail-section job-review-section">
      <div className="job-detail-section-heading"><span className="eyebrow">EMPLOYEE REVIEWS</span><h2>회사 리뷰</h2></div>
      <p className="job-review-guide">이력서의 경력 회사명과 공고 회사명이 일치하면 ‘재직 이력 확인’ 배지가 자동으로 붙습니다. 민감한 증빙 서류는 공개하지 않습니다.</p>
      <div className="job-review-form"><label>별점<select value={reviewRating} onChange={(event) => setReviewRating(Number(event.target.value))}>{[5,4,3,2,1].map((value) => <option key={value} value={value}>{value}점</option>)}</select></label><label>리뷰<textarea minLength={10} maxLength={2000} value={reviewContent} onChange={(event) => setReviewContent(event.target.value)} placeholder="근무 경험, 조직 문화, 성장 환경을 10자 이상 적어 주세요." /></label><button type="button" className="primary-button" disabled={reviewContent.trim().length < 10} onClick={() => void saveReview()}>리뷰 저장</button>{reviewMessage && <p role="status">{reviewMessage}</p>}</div>
      <div className="job-review-list">{reviews.length === 0 ? <p className="job-review-empty">아직 등록된 리뷰가 없습니다.</p> : reviews.map((review) => <article key={review.id}><div><span className="job-review-stars"><Star size={14} fill="currentColor" /> {review.rating}.0</span>{review.employmentVerified && <span className="job-review-verified"><BadgeCheck size={14} />재직 이력 확인</span>}{review.mine && <small>내 리뷰</small>}<time>{dateLabel(review.createdAt)}</time></div><p>{review.content}</p></article>)}</div>
    </section>
    <CompanyFinanceSection postingId={posting.id} />
    {selectedMatch && <JobMatchDrawer job={selectedMatch} interested={interested} onInterest={() => void toggleInterest(posting.id)} onClose={() => setSelectedMatch(null)} />}
  </div>;
}

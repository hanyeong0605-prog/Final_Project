import { useEffect, useState } from "react";
import { ArrowLeft, BriefcaseBusiness, Building2, CalendarDays, ExternalLink, MapPin } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { getJobPosting } from "../features/job-postings/api/jobPostingsApi";
import type { JobPostingDetail } from "../features/job-postings/model/jobPosting.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";

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

  useEffect(() => {
    if (!id) return;
    setStatus("loading");
    void getJobPosting(id)
      .then((data) => { setPosting(data); setStatus("ready"); })
      .catch(() => { setPosting(null); setStatus("error"); });
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

  return <div className="job-detail-page">
    <Link className="job-detail-back" to="/job-postings"><ArrowLeft size={16} />전체 채용공고</Link>
    <section className="job-detail-hero">
      <div className="job-detail-company">
        {posting.companyLogoUrl ? <img src={posting.companyLogoUrl} alt={`${posting.companyName ?? "회사"} 로고`} onError={(event) => { event.currentTarget.style.display = "none"; }} /> : <Building2 size={30} />}
        {hasText(posting.companyName) && <span>{posting.companyName}</span>}
      </div>
      <h1>{posting.title}</h1>
      {(hasText(locationText) || workType || deadline) && <div className="job-detail-meta">
        {hasText(locationText) && <span><MapPin size={16} />{locationText}</span>}
        {workType && <span><BriefcaseBusiness size={16} />{workType}</span>}
        {deadline && <span><CalendarDays size={16} />마감 {deadline}</span>}
      </div>}
      <a className="primary-button job-source-link" href={posting.sourceUrl} target="_blank" rel="noreferrer">공고 원문 보기<ExternalLink size={16} /></a>
    </section>

    {images.length > 0 && <section className="job-detail-section"><div className="job-detail-section-heading"><span className="eyebrow">COMPANY IMAGES</span><h2>공고 이미지</h2></div><div className="job-image-gallery">{images.map((url) => <img key={url} src={url} alt={`${posting.companyName ?? "회사"} 공고 이미지`} loading="lazy" onError={(event) => { event.currentTarget.style.display = "none"; }} />)}</div></section>}

    {(hasText(posting.description) || details.length > 0) && <div className="job-detail-layout">
      {hasText(posting.description) && <section className="job-detail-section job-description"><div className="job-detail-section-heading"><span className="eyebrow">JOB DESCRIPTION</span><h2>공고 상세</h2></div><p>{posting.description}</p></section>}
      {details.length > 0 && <aside className="job-detail-section job-information"><div className="job-detail-section-heading"><span className="eyebrow">JOB INFORMATION</span><h2>공고 정보</h2></div><dl>{details.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl></aside>}
    </div>}
  </div>;
}

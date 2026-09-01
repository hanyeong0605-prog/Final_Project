import { Bookmark, BriefcaseBusiness, Building2, CalendarDays, Landmark, MapPin } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useInterests } from "../../interests/model/InterestContext";
import type { JobPosting } from "../model/jobPosting.types";

function hasText(value: string | null | undefined): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function dateLabel(value: string) {
  return new Intl.DateTimeFormat("ko-KR").format(new Date(value));
}

function scheduleLabel(posting: JobPosting): string | null {
  if (posting.rollingDeadline) return "상시 채용";
  if (hasText(posting.deadlineAt)) return `마감 ${dateLabel(posting.deadlineAt)}`;
  if (hasText(posting.publishedAt)) return `등록 ${dateLabel(posting.publishedAt)}`;
  return null;
}

export function JobPostingCard({ posting }: { posting: JobPosting }) {
  const { isInterested, toggleInterest } = useInterests();
  const navigate = useNavigate();
  const [imageFailed, setImageFailed] = useState(false);
  const [logoFailed, setLogoFailed] = useState(false);
  const interested = isInterested(posting.id);
  const previewImageUrl = posting.thumbnailUrl;
  const companyLogoUrl = posting.companyLogoUrl;
  const skills = (posting.jobName || posting.keywords || "").split(",").map((value) => value.trim()).filter(Boolean).slice(0, 5);
  const workType = [posting.experienceType, posting.employmentType].filter(hasText).join(" · ");
  const schedule = scheduleLabel(posting);

  const openDetail = () => navigate(`/job-postings/${posting.id}`);
  return <article className="posting-card" role="link" tabIndex={0} onClick={openDetail} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); openDetail(); } }} aria-label={`${posting.title} 상세 보기`}>
    <div className="posting-card-top">
      <div className="posting-company-identity">
        {hasText(companyLogoUrl) && !logoFailed
          ? <img src={companyLogoUrl} alt="" onError={() => setLogoFailed(true)} />
          : <Building2 size={17} />}
        {hasText(posting.companyName) && <span className="company-name">{posting.companyName}</span>}
      </div>
      <span
        className={posting.hasFinancials ? "posting-finance-badge available" : "posting-finance-badge unavailable"}
        title={posting.hasFinancials ? "DART 재무제표가 연결된 기업입니다" : "현재 연결된 DART 재무제표가 없습니다"}
      >
        <Landmark size={12} />{posting.hasFinancials ? "DART 재무 있음" : "재무 미연결"}
      </span>
      <button className={interested ? "bookmark active" : "bookmark"} onClick={(event) => { event.stopPropagation(); void toggleInterest(posting.id); }} aria-label="관심 공고 등록"><Bookmark size={19} fill={interested ? "currentColor" : "none"} /></button>
    </div>
    <div className="posting-card-preview">
      {hasText(previewImageUrl) && !imageFailed
        ? <img src={previewImageUrl} alt={`${posting.companyName ?? "채용 기업"} 공고 미리보기`} onError={() => setImageFailed(true)} />
        : null}
    </div>
    <div className="posting-card-main">
      <h2>{posting.title}</h2>
      {(hasText(posting.location) || workType || schedule) && <div className="posting-meta">
        {hasText(posting.location) && <span><MapPin size={14} />{posting.location}</span>}
        {workType && <span><BriefcaseBusiness size={14} />{workType}</span>}
        {schedule && <span><CalendarDays size={14} />{schedule}</span>}
      </div>}
      {skills.length > 0 && <div className="skills">{skills.map((skill) => <span key={skill}>{skill}</span>)}</div>}
    </div>
    <div className="posting-footer">{hasText(posting.salary) && <strong>{posting.salary}</strong>}<span>상세 보기</span></div>
  </article>;
}

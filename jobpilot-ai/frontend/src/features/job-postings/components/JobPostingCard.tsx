import { Bookmark, BriefcaseBusiness, CalendarDays, MapPin } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useInterests } from "../../interests/model/InterestContext";
import type { JobPosting } from "../model/jobPosting.types";

function dateLabel(value: string | null, rolling: boolean) {
  if (rolling) return "상시 채용";
  if (!value) return "마감일 미등록";
  return new Intl.DateTimeFormat("ko-KR").format(new Date(value));
}

function periodLabel(posting: JobPosting) {
  const start = posting.publishedAt ? new Intl.DateTimeFormat("ko-KR").format(new Date(posting.publishedAt)) : "시작일 미등록";
  return `${start} ~ ${dateLabel(posting.deadlineAt, posting.rollingDeadline)}`;
}

export function JobPostingCard({ posting }: { posting: JobPosting }) {
  const { isInterested, toggleInterest } = useInterests();
  const navigate = useNavigate();
  const interested = isInterested(posting.id);
  const skills = (posting.jobName || posting.keywords || "").split(",").map((value) => value.trim()).filter(Boolean).slice(0, 5);

  return <article className="posting-card">
    <div className="posting-card-top">
      <button className={interested ? "bookmark active" : "bookmark"} onClick={() => void toggleInterest(posting.id)} aria-label="관심 공고 등록"><Bookmark size={19} fill={interested ? "currentColor" : "none"} /></button>
    </div>
    <button className="posting-card-main" type="button" onClick={() => navigate(`/job-postings/${posting.id}`)} aria-label={`${posting.title} 상세 보기`}>
      <span className="company-name">{posting.companyName ?? "회사명 미등록"}</span>
      <h2>{posting.title}</h2>
      <div className="posting-meta">
        <span><MapPin size={14} />{posting.location ?? "근무지 미등록"}</span>
        <span><BriefcaseBusiness size={14} />{posting.experienceType ?? "경력 무관"} · {posting.employmentType ?? "고용형태 미등록"}</span>
        <span><CalendarDays size={14} />{periodLabel(posting)}</span>
      </div>
      {skills.length > 0 && <div className="skills">{skills.map((skill) => <span key={skill}>{skill}</span>)}</div>}
    </button>
    <div className="posting-footer"><strong>{posting.salary ?? "급여 정보 없음"}</strong><span>상세 보기</span></div>
  </article>;
}

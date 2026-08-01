import { Bookmark, BriefcaseBusiness, CalendarDays, MapPin } from "lucide-react";
import { useInterests } from "../../interests/model/InterestContext";
import type { JobPosting } from "../model/jobPosting.types";

function dateLabel(value: string | null, rolling: boolean) {
  if (rolling) return "상시채용";
  if (!value) return "마감일 미등록";
  return new Intl.DateTimeFormat("ko-KR").format(new Date(value));
}
function periodLabel(posting: JobPosting) {
  const start = posting.publishedAt ? new Intl.DateTimeFormat("ko-KR").format(new Date(posting.publishedAt)) : "시작일 미등록";
  return `${start} ~ ${dateLabel(posting.deadlineAt, posting.rollingDeadline)}`;
}

export function JobPostingCard({ posting }: { posting: JobPosting }) {
  const { isInterested, toggleInterest } = useInterests();
  const interested = isInterested(posting.id);
  const skills = (posting.jobName || posting.keywords || "").split(",").map((value) => value.trim()).filter(Boolean).slice(0, 5);

  return <article className="posting-card">
    <div className="posting-card-top">
      <span className="source-badge">사람인</span>
      <button className={interested ? "bookmark active" : "bookmark"} onClick={() => void toggleInterest(posting.id)} aria-label="관심 공고 등록"><Bookmark size={19} fill={interested ? "currentColor" : "none"} /></button>
    </div>
    <span className="company-name">{posting.companyName ?? "회사명 미등록"}</span>
    <h2>{posting.title}</h2>
    <div className="posting-meta">
      <span><MapPin size={14} />{posting.location ?? "근무지역 미등록"}</span>
      <span><BriefcaseBusiness size={14} />{posting.experienceType ?? "경력 무관"} · {posting.employmentType ?? "고용형태 미등록"}</span>
      <span><CalendarDays size={14} />{periodLabel(posting)}</span>
    </div>
    {skills.length > 0 && <div className="skills">{skills.map((skill) => <span key={skill}>{skill}</span>)}</div>}
    <div className="posting-footer"><strong>{posting.salary ?? "급여 정보 없음"}</strong><a href={posting.sourceUrl} target="_blank" rel="noreferrer">사람인 원문 보기</a></div>
  </article>;
}

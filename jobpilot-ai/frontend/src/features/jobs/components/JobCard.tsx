import { Bookmark, CalendarDays, ChevronRight, MapPin } from "lucide-react";
import { Link } from "react-router-dom";
import { gradeMeta } from "../model/job.constants";
import type { JobMatch } from "../model/job.types";

interface JobCardProps { job: JobMatch; interested: boolean; onOpen: () => void; onInterest: () => void; }

export function JobCard({ job, interested, onOpen, onInterest }: JobCardProps) {
  const meta = gradeMeta[job.recommendationLevel];
  return <article className="job-card">
    <div className="card-top"><span className="source-badge">{job.source}</span><button className={interested ? "bookmark active" : "bookmark"} onClick={onInterest} aria-label="관심 등록"><Bookmark size={19} fill={interested ? "currentColor" : "none"} /></button></div>
    <span className="company-name">{job.company}</span><h2>{job.title}</h2>
    <p className="job-meta"><MapPin size={15} />{job.location}<i /><CalendarDays size={14} />마감 {job.deadline || "미등록"}</p>
    <div className="skills">{job.skills.slice(0, 4).map((skill) => <span key={skill}>{skill}</span>)}</div>
    <div className="job-card-summary"><span className={`grade-chip ${meta.tone}`}>{meta.label}</span><strong>{job.score}<small>점</small></strong><p>{job.comment}</p></div>
    <div className="card-actions"><button className="outline-button" onClick={onOpen}>매칭 근거 보기</button><Link to={`/job-postings/${job.id}`}>채용 공고 확인 <ChevronRight size={14} /></Link></div>
  </article>;
}

import { Bookmark, ChevronRight, MapPin } from "lucide-react";
import { gradeMeta } from "../model/job.constants";
import type { JobMatch } from "../model/job.types";

interface JobCardProps {
  job: JobMatch;
  interested: boolean;
  onOpen: () => void;
  onInterest: () => void;
}

export function JobCard({ job, interested, onOpen, onInterest }: JobCardProps) {
  const meta = gradeMeta[job.grade];
  return <article className="job-card"><div className="card-top"><span className="source-badge">{job.source}</span><button className={interested ? "bookmark active" : "bookmark"} onClick={onInterest} aria-label="관심 등록"><Bookmark size={19} fill={interested ? "currentColor" : "none"} /></button></div><span className="company-name">{job.company}</span><h2>{job.title}</h2><p className="job-meta"><MapPin size={15} />{job.location}<i />신입·경력무관</p><div className="skills">{job.skills.slice(0, 4).map((skill) => <span key={skill}>{skill}</span>)}</div><div className="job-card-summary"><span className={`grade-chip ${meta.tone}`}>{meta.label}</span><strong>{job.score}<small>점</small></strong><p>{job.comment}</p></div><div className="card-actions"><button className="outline-button" onClick={onOpen}>매칭 근거 보기</button><a href="#source">원문 공고 <ChevronRight size={14} /></a></div></article>;
}

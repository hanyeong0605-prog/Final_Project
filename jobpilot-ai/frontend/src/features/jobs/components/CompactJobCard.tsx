import { Bookmark, MapPin } from "lucide-react";
import { gradeMeta } from "../model/job.constants";
import type { JobMatch } from "../model/job.types";

interface CompactJobCardProps {
  job: JobMatch;
  interested: boolean;
  onOpen: () => void;
  onInterest: () => void;
}

export function CompactJobCard({ job, interested, onOpen, onInterest }: CompactJobCardProps) {
  const meta = gradeMeta[job.recommendationLevel];
  return <article className="compact-job"><div className="company-row"><span className="source-badge">{job.source}</span><span>{job.company}</span><button className={interested ? "bookmark active" : "bookmark"} onClick={onInterest} aria-label="관심 등록"><Bookmark size={18} fill={interested ? "currentColor" : "none"} /></button></div><button className="job-link" onClick={onOpen}><h3>{job.title}</h3><p><MapPin size={14} />{job.location} <i /> 마감 {job.deadline}</p></button><div className="job-footer"><span className={`grade-chip ${meta.tone}`}>{meta.label}</span><strong>{job.score}<small>점</small></strong></div></article>;
}

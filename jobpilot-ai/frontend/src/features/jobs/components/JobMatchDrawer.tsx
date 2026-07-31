import { Bookmark, ChevronRight, MapPin, X } from "lucide-react";
import { evidenceMeta, gradeMeta } from "../model/job.constants";
import type { JobMatch } from "../model/job.types";

interface JobMatchDrawerProps {
  job: JobMatch;
  interested: boolean;
  onInterest: () => void;
  onClose: () => void;
}

export function JobMatchDrawer({ job, interested, onInterest, onClose }: JobMatchDrawerProps) {
  const meta = gradeMeta[job.grade];
  return <div className="drawer-layer" role="dialog" aria-modal="true" aria-label="공고 매칭 근거"><div className="drawer-backdrop" onClick={onClose} /><aside className="job-drawer"><header><button className="drawer-close" onClick={onClose} aria-label="닫기"><X size={20} /></button><span className="source-badge">{job.source}</span><span className="company-name">{job.company}</span><h2>{job.title}</h2><p className="job-meta"><MapPin size={15} />{job.location}<i />마감 {job.deadline}</p></header><section className="match-overview"><span className={`grade-chip ${meta.tone}`}>{meta.label}</span><div><strong>{job.score}<small>점</small></strong><span>지원 준비도</span></div><p>{job.comment}</p></section><section className="matrix-section"><div className="matrix-title"><div><span className="eyebrow">WHY THIS RESULT</span><h3>요구사항 · 내 근거 매트릭스</h3></div><p>점수만으로 판단하지 않고, 원문 공고의 요구사항별로 근거를 확인합니다.</p></div><div className="matrix-list">{job.requirements.map((item) => { const evidence = evidenceMeta[item.status]; return <article key={item.requirement} className="matrix-row"><div className="requirement"><span>{item.requirementType}</span><strong>{item.requirement}</strong></div><div className="evidence"><span className={`status-chip ${evidence.tone}`}>{evidence.label}</span><strong>{item.evidence}</strong><p>다음 행동: {item.action}</p></div></article>; })}</div></section><footer><button className="outline-button" onClick={onInterest}><Bookmark size={17} fill={interested ? "currentColor" : "none"} />{interested ? "관심 목록에 저장됨" : "관심 목록에 저장"}</button><a className="primary-button" href="#source">원문 공고 확인 <ChevronRight size={16} /></a></footer></aside></div>;
}

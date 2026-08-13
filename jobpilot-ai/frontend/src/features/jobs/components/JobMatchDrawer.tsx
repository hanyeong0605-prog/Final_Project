import { useMemo, useRef, useState } from "react";
import { Bookmark, ChevronRight, ExternalLink, MapPin, X } from "lucide-react";
import { Link } from "react-router-dom";
import { evidenceMeta, gradeMeta } from "../model/job.constants";
import type { JobMatch, RequirementEvidence } from "../model/job.types";

interface JobMatchDrawerProps {
  job: JobMatch;
  interested: boolean;
  onInterest: () => void;
  onClose: () => void;
}

function normalized(value: string) {
  return value.toLocaleLowerCase().replaceAll(/\s+/g, " ").trim();
}

function sourceText(item: RequirementEvidence) {
  return item.sourceExcerpt.trim() || item.requirement.trim();
}

function matchingNumbers(text: string, requirements: RequirementEvidence[]) {
  const source = normalized(text);
  return requirements
    .filter((item) => {
      const excerpt = normalized(sourceText(item));
      return excerpt.length >= 2 && source.includes(excerpt);
    })
    .map((item) => item.sourceNumber);
}

export function JobMatchDrawer({ job, interested, onInterest, onClose }: JobMatchDrawerProps) {
  const meta = gradeMeta[job.recommendationLevel];
  const [activeEvidence, setActiveEvidence] = useState<number | null>(null);
  const sourceParagraphRefs = useRef<Map<number, HTMLParagraphElement>>(new Map());
  const paragraphs = useMemo(
    () => job.postingDescription.split(/\n+/).map((item) => item.trim()).filter(Boolean),
    [job.postingDescription],
  );

  const focusEvidence = (sourceNumber: number) => {
    setActiveEvidence(sourceNumber);
    sourceParagraphRefs.current.get(sourceNumber)?.scrollIntoView({ behavior: "smooth", block: "center" });
  };

  return <div className="drawer-layer" role="dialog" aria-modal="true" aria-label="채용공고 매칭 근거">
    <div className="drawer-backdrop" onClick={onClose} />
    <aside className="job-drawer evidence-drawer">
      <header>
        <button className="drawer-close" onClick={onClose} aria-label="닫기"><X size={20} /></button>
        <span className="source-badge">{job.source}</span><span className="company-name">{job.company}</span>
        <h2>{job.title}</h2>
        <p className="job-meta"><MapPin size={15} />{job.location}<i />마감 {job.deadline}</p>
      </header>

      <section className="match-overview">
        <span className={`grade-chip ${meta.tone}`}>{meta.label}</span>
        <div><strong>{job.score}<small>점</small></strong><span>지원 준비도</span></div>
        <p>{job.comment}</p>
      </section>

      <div className="match-evidence-layout">
        <section className="original-posting-pane">
          <div className="original-pane-heading">
            <div><span className="eyebrow">ORIGINAL POSTING</span><h3>공고 원문</h3></div>
            <a href={job.sourceUrl} target="_blank" rel="noreferrer">원문 열기 <ExternalLink size={14} /></a>
          </div>
          <p className="original-pane-guide">색으로 표시된 문장이 오른쪽 매트릭스의 같은 번호 근거입니다.</p>
          <article className="original-posting-copy">
            {paragraphs.length > 0 ? paragraphs.map((paragraph, index) => {
              const numbers = matchingNumbers(paragraph, job.requirements);
              const active = activeEvidence !== null && numbers.includes(activeEvidence);
              return <p
                key={`${index}-${paragraph.slice(0, 12)}`}
                ref={(element) => {
                  if (!element) return;
                  numbers.forEach((number) => {
                    if (!sourceParagraphRefs.current.has(number)) sourceParagraphRefs.current.set(number, element);
                  });
                }}
                className={numbers.length ? `source-paragraph${active ? " source-active" : ""}` : ""}
              >
                {numbers.length > 0 && <span className="source-number-list">{numbers.map((number) => <b key={number}>#{number}</b>)}</span>}
                {paragraph}
              </p>;
            }) : <div className="original-posting-empty">저장된 공고 본문이 없습니다. 아래 원문 공고에서 내용을 확인해 주세요.</div>}
          </article>
        </section>

        <section className="matrix-section">
          <div className="matrix-title">
            <div><span className="eyebrow">WHY THIS RESULT</span><h3>요구사항 · 내 근거 매트릭스</h3></div>
            <p>각 항목을 누르면 왼쪽 원문에서 연결된 근거를 강조합니다.</p>
          </div>
          <div className="matrix-list">
            {job.requirements.map((item) => {
              const evidence = evidenceMeta[item.status];
              const isActive = activeEvidence === item.sourceNumber;
              return <button type="button" key={item.requirementId ?? `${item.sourceNumber}-${item.requirement}`} className={`matrix-row${isActive ? " source-active" : ""}`} onClick={() => focusEvidence(item.sourceNumber)}>
                <div className="requirement">
                  <span>{item.requirementType}</span>
                  <strong><em className="matrix-number">#{item.sourceNumber}</em>{item.requirement}</strong>
                  <small>원문 근거: {sourceText(item)}</small>
                </div>
                <div className="evidence">
                  <span className={`status-chip ${evidence.tone}`}>{evidence.label}</span>
                  <strong>{item.evidence}</strong>
                  <p>다음 행동: {item.action}</p>
                </div>
              </button>;
            })}
          </div>
        </section>
      </div>

      <footer>
        <button className="outline-button" onClick={onInterest}><Bookmark size={17} fill={interested ? "currentColor" : "none"} />{interested ? "관심 목록에 저장됨" : "관심 목록에 저장"}</button>
        <Link className="primary-button" to={`/job-postings/${job.id}`}>채용 공고 확인 <ChevronRight size={16} /></Link>
      </footer>
    </aside>
  </div>;
}

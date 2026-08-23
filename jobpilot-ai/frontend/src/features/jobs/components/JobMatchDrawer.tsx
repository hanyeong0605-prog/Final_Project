import { useEffect, useMemo, useRef, useState, type CSSProperties, type PointerEvent } from "react";
import { Bookmark, ChevronRight, ChevronsDown, ExternalLink, GripVertical, MapPin, X } from "lucide-react";
import { Link } from "react-router-dom";
import { evidenceMeta, gradeMeta } from "../model/job.constants";
import type { JobMatch, RequirementEvidence } from "../model/job.types";
import type { GrowthAction } from "../model/job.types";
import { getGrowthActions } from "../api/jobMatchesApi";

interface JobMatchDrawerProps {
  job: JobMatch;
  evidenceLoading?: boolean;
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

export function JobMatchDrawer({ job, evidenceLoading = false, interested, onInterest, onClose }: JobMatchDrawerProps) {
  const meta = gradeMeta[job.recommendationLevel];
  const [activeEvidenceNumbers, setActiveEvidenceNumbers] = useState<number[]>([]);
  const [growthActions, setGrowthActions] = useState<GrowthAction[]>([]);
  const [sourcePaneRatio, setSourcePaneRatio] = useState(33);
  const sourceParagraphRefs = useRef<Map<number, HTMLParagraphElement>>(new Map());
  const evidenceLayoutRef = useRef<HTMLDivElement>(null);
  const growthPlanHeadingRef = useRef<HTMLDivElement>(null);
  const paragraphs = useMemo(
    () => job.postingDescription.split(/\n+/).map((item) => item.trim()).filter(Boolean),
    [job.postingDescription],
  );
  useEffect(() => {
    if (evidenceLoading) { setGrowthActions([]); return; }
    void getGrowthActions(job.id).then(setGrowthActions).catch(() => setGrowthActions([]));
  }, [job.id, evidenceLoading]);

  const focusEvidence = (sourceNumbers: number[]) => {
    const uniqueNumbers = [...new Set(sourceNumbers)];
    setActiveEvidenceNumbers(uniqueNumbers);
    sourceParagraphRefs.current.get(uniqueNumbers[0])?.scrollIntoView({ behavior: "smooth", block: "center" });
  };
  const beginPaneResize = (event: PointerEvent<HTMLButtonElement>) => {
    event.preventDefault();
    const layout = evidenceLayoutRef.current;
    if (!layout) return;
    const onMove = (moveEvent: globalThis.PointerEvent) => {
      const bounds = layout.getBoundingClientRect();
      const ratio = ((moveEvent.clientX - bounds.left) / bounds.width) * 100;
      setSourcePaneRatio(Math.min(52, Math.max(22, ratio)));
    };
    const onEnd = () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onEnd);
    };
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onEnd, { once: true });
  };
  const scrollToGrowthPlan = () => growthPlanHeadingRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  return <div className="drawer-layer" role="dialog" aria-modal="true" aria-label="채용공고 매칭 근거">
    <div className="drawer-backdrop" onClick={onClose} />
    <aside className="job-drawer evidence-drawer">
      <header>
        <button className="drawer-close" onClick={onClose} aria-label="닫기"><X size={20} /></button>
        <span className="source-badge">{job.source}</span><span className="company-name">{job.company}</span>
        <h2>{job.title}</h2>
        <p className="job-meta"><MapPin size={15} />{job.location}<i />마감 {job.deadline}</p>
      </header>

      <section className={`match-overview ${meta.tone}`}>
        <span className={`grade-chip ${meta.tone}`}>{meta.label}</span>
        <div><strong>{job.score}<small>점</small></strong><span>지원 준비도</span></div>
        <p>{job.comment}</p>
      </section>

      <div className="match-evidence-layout" ref={evidenceLayoutRef} style={{ "--source-pane-ratio": `${sourcePaneRatio}%` } as CSSProperties}>
        <section className="original-posting-pane evidence-pane">
          <div className="original-pane-heading">
            <div><span className="eyebrow">ORIGINAL POSTING</span><h3>공고 원문</h3></div>
            <a href={job.sourceUrl} target="_blank" rel="noreferrer">원문 열기 <ExternalLink size={14} /></a>
          </div>
          <p className="original-pane-guide">색으로 표시된 문장이 오른쪽 매트릭스의 같은 번호 근거입니다.</p>
          <article className="original-posting-copy">
            {paragraphs.length > 0 ? paragraphs.map((paragraph, index) => {
              const numbers = matchingNumbers(paragraph, job.requirements);
              const active = numbers.some((number) => activeEvidenceNumbers.includes(number));
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

        <button className="evidence-pane-resizer" type="button" aria-label="공고 원문과 근거 매트릭스 너비 조절" onPointerDown={beginPaneResize}><GripVertical size={16} /></button>
        <section className="matrix-section evidence-pane">
          <div className="matrix-title">
            <div><span className="eyebrow">WHY THIS RESULT</span><h3>요구사항 · 내 근거 매트릭스</h3></div>
            <p>각 항목을 누르면 왼쪽 원문에서 연결된 근거를 강조합니다.</p>
          </div>
          <button className="growth-plan-jump" type="button" onClick={scrollToGrowthPlan}><ChevronsDown size={16} /> 부족 요건 보강 플랜 <ChevronsDown size={16} /></button>
          {evidenceLoading ? <div className="match-evidence-loading"><span className="match-evidence-spinner" /><strong>근거 가져오는 중</strong><p>회원님의 프로젝트·경력 이력에서 요구사항별 증거 문장을 찾고 있습니다.</p></div> : <div className="matrix-list">
            {job.requirements.map((item) => {
              const evidence = evidenceMeta[item.status];
              const isActive = activeEvidenceNumbers.includes(item.sourceNumber);
              return <button type="button" key={item.requirementId ?? `${item.sourceNumber}-${item.requirement}`} className={`matrix-row${isActive ? " source-active" : ""}`} onClick={() => focusEvidence([item.sourceNumber])}>
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
                <div className="member-evidence">
                  <span>내 이력 근거</span>
                  {item.memberEvidence ? <strong>{item.memberEvidence}</strong> : <p>{item.status === "DIRECT" ? "직접 증명 항목이지만, 저장된 이력 문장과 연결되지 않았습니다." : "연결 가능한 이력 근거가 없습니다."}</p>}
                </div>
              </button>;
            })}
          </div>}
          <section className="matrix-growth-plan" ref={growthPlanHeadingRef} tabIndex={-1}>
            <div className="growth-plan-heading"><div><span className="eyebrow">GROWTH PLAN</span><h3>부족 요건 보강 플랜</h3><p>겹치는 보강 방법은 한 카드로 묶었습니다. 카드를 누르면 관련 근거 전체를 강조합니다.</p></div></div>
            {growthActions.length === 0 ? <div className="growth-plan-empty">현재 확인된 부족 요건이 없습니다.</div> : <div className="growth-plan-list">{growthActions.map((action) => {
            const requirementIds = action.relatedRequirementIds?.length ? action.relatedRequirementIds : typeof action.requirementId === "number" ? [action.requirementId] : [];
            const requirements = job.requirements.filter((item) => typeof item.requirementId === "number" && requirementIds.includes(item.requirementId));
            const sourceNumbers = requirements.map((item) => item.sourceNumber);
            return <article className="growth-plan-card" key={`${action.category}-${action.title}-${action.requirementId ?? "group"}`}><button type="button" onClick={() => sourceNumbers.length > 0 && focusEvidence(sourceNumbers)}><span>{action.category}</span><strong>{action.title}</strong><p>{action.description}</p>{requirements.length > 0 && <small>공고 근거 {requirements.map((item) => `#${item.sourceNumber}`).join(" · ")} · {action.requirement}</small>}</button>{action.recommendations?.length ? <div className="growth-resource-list">{action.recommendations.map((resource) => resource.href.startsWith("http") ? <a key={resource.type} href={resource.href} target="_blank" rel="noreferrer" className="growth-resource-link"><span>{resource.label}</span><strong>{resource.title}</strong><small>{resource.description}</small><ChevronRight size={13} /></a> : <Link key={resource.type} to={`${resource.href}${resource.href.includes("?") ? "&" : "?"}jobPostingId=${job.id}`} className="growth-resource-link"><span>{resource.label}</span><strong>{resource.title}</strong><small>{resource.description}</small><ChevronRight size={13} /></Link>)}</div> : <Link to={`/opportunities?jobPostingId=${job.id}&requirementId=${action.requirementId ?? ""}`} className="growth-detail-link">자세히 보기 <ChevronRight size={14} /></Link>}</article>;
            })}</div>}
          </section>
        </section>
      </div>

      <footer>
        <button className="outline-button" onClick={onInterest}><Bookmark size={17} fill={interested ? "currentColor" : "none"} />{interested ? "관심 목록에 저장됨" : "관심 목록에 저장"}</button>
        <Link className="primary-button" to={`/job-postings/${job.id}`}>채용 공고 확인 <ChevronRight size={16} /></Link>
      </footer>
    </aside>
  </div>;
}

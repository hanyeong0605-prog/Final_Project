import { useEffect, useState } from "react";
import { ChevronDown, ChevronUp, FilePenLine, FileUp, Sparkles, Target } from "lucide-react";
import { useSearchParams } from "react-router-dom";
import {
  ResumeProfileAnalysisSection,
  ResumeWritingAssistantSection,
} from "../features/resume/components/ResumeDocumentSection";
import { ProfilePage } from "./ProfilePage";
import { PageHeading } from "../shared/components/PageHeading";
import { getCareerProfile } from "../features/profile/api/careerProfileApi";
import { getMemberSkills } from "../features/profile/api/memberSkillsApi";
import { getMemberCertificates } from "../features/profile/api/memberCertificatesApi";
import { listResumeEntries } from "../features/resume/api/resumeEntriesApi";
import { listSelfIntroductions } from "../features/resume/api/resumeApi";
import { getResumeSaveState, type ResumeSaveState } from "../features/resume/api/resumeSaveStateApi";
import type { ResumeEntryType } from "../features/resume/model/resumeEntry.types";

type CapabilityTool = "profile" | "manage" | "analysis" | "writer" | null;

export function CapabilityManagementPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTool = searchParams.get("tool") === "profile" ? "profile" : null;
  const [openTool, setOpenTool] = useState<CapabilityTool>(requestedTool);
  useEffect(() => { if (requestedTool) setOpenTool(requestedTool); }, [requestedTool]);
  const closeTool = () => { setOpenTool(null); setSearchParams({}); };

  const heading = openTool === "analysis"
    ? ["이력서 분석 · 스펙 자동 채우기", "기존 이력서에서 발견한 정보만 제안합니다. 질문이나 양식 선택 없이, 확인 후 원하는 항목만 프로필에 반영하세요."]
    : openTool === "writer"
      ? ["이력서 작성 도우미", "내 역량을 불러오고 양식과 질문 답변을 선택하면, AI가 사실에 근거한 수정 가능한 Word 초안을 만듭니다."]
      : ["역량 관리", "내 스펙을 정리하고, 기존 이력서에서 정보를 자동 추출하거나 역량을 바탕으로 수정 가능한 이력서 초안을 만드세요."];

  return <>
    <PageHeading eyebrow="CAPABILITY MANAGEMENT" title={heading[0]} body={heading[1]} />

    <section className="capability-tool-grid" aria-label="역량 관리 도구">
      <article className={`capability-tool-card ${openTool === "profile" || openTool === "analysis" ? "selected" : ""}`}>
        <span className="capability-tool-icon"><Target size={21} /></span>
        <div>
          <span className="eyebrow">MY CAPABILITY</span>
          <h2>나의 스펙 정보</h2>
          <p>희망 직무, 경력, 기술 스택, 자격증, 학력을 직접 관리하거나 기존 이력서에서 자동으로 찾아볼 수 있어요.</p>
        </div>
        <div className="capability-tool-actions">
          <button className="primary-button" onClick={() => setOpenTool("profile")}>스펙정보 입력하기</button>
          <button className="outline-button" onClick={() => setOpenTool("manage")}>스펙정보 관리</button>
        </div>
      </article>
      <article className={`capability-tool-card ${openTool === "writer" ? "selected" : ""}`}>
        <span className="capability-tool-icon"><FilePenLine size={21} /></span>
        <div>
          <span className="eyebrow">RESUME ASSISTANT</span>
          <h2>이력서 작성 도우미</h2>
          <p>저장된 역량을 선택해 불러오고, 원하는 양식과 질문 답변을 바탕으로 AI 이력서 초안을 만듭니다.</p>
        </div>
        <div className="capability-tool-actions">
          <button className="primary-button" onClick={() => setOpenTool("writer")}>내 역량 불러와 이력서 작성 <Sparkles size={15} /></button>
        </div>
      </article>
    </section>

    {openTool === "profile" && <section className="panel capability-open-panel"><ToolHeader title="나의 스펙 정보 입력" body="이력서를 첨부해 자동으로 채우거나, 필요한 항목을 직접 입력해 저장할 수 있습니다." action={() => setOpenTool("analysis")} close={closeTool} /><ProfilePage /></section>}
    {openTool === "manage" && <section className="panel capability-open-panel"><ToolHeader title="저장된 스펙정보" body="저장된 이력 항목과 자기소개서, 보유 스펙을 한눈에 확인하고 필요한 항목을 수정할 수 있습니다." close={closeTool} /><SavedCapabilityList onEdit={() => setOpenTool("profile")} /></section>}
    {openTool === "analysis" && <section className="panel capability-open-panel"><ToolHeader title="이력서 첨부하고 자동 채우기" body="PDF/DOCX 이력서에서 발견한 정보만 스펙 제안으로 보여드립니다. 발견되지 않은 항목은 직접 기재해 주세요." close={closeTool} /><ResumeProfileAnalysisSection /></section>}
    {openTool === "writer" && <section className="panel capability-open-panel"><ToolHeader title="이력서 작성 도우미" body="역량 불러오기 → 양식 선택 → 질문 답변 → AI 초안 생성 순서로 진행합니다." close={closeTool} /><ResumeWritingAssistantSection /></section>}
    {!openTool && <section className="capability-guide panel"><Sparkles size={20} /><div><strong>기존 이력서가 있다면 먼저 분석해 보세요.</strong><p>추출 결과는 자동 저장되지 않습니다. 확인 후 프로필 반영을 누른 항목만 내 스펙과 채용공고 추천에 사용됩니다.</p></div></section>}
  </>;
}

type SavedCapabilityItem = { label: string; count: number; updatedAt?: string; details: string[] };
const displayValue = (value: unknown) => value === null || value === undefined || value === "" ? null : String(value);
const entrySummary = (entry: { title: string; content: Record<string, unknown> }) => [entry.title, ...Object.values(entry.content).map(displayValue).filter((value): value is string => Boolean(value))].join(" · ");

export function SavedCapabilityList({ onEdit, readOnly = false }: { onEdit?: () => void; readOnly?: boolean }) {
  const [loading, setLoading] = useState(true); const [message, setMessage] = useState(""); const [items, setItems] = useState<SavedCapabilityItem[]>([]); const [expanded, setExpanded] = useState<string | null>(null); const [saveState, setSaveState] = useState<ResumeSaveState>({ status: "NOT_SAVED", updatedAt: null });
  useEffect(() => {
    void Promise.all([getCareerProfile(), getMemberSkills(), getMemberCertificates(), listResumeEntries(), listSelfIntroductions(), getResumeSaveState()])
      .then(([profile, skills, certificates, entries, introductions, state]) => {
        setSaveState(state);
        const entryLabels: Partial<Record<ResumeEntryType, string>> = { EDUCATION: "학력", CAREER: "경력", ACTIVITY: "인턴 · 대외활동", TRAINING: "교육이수", AWARD: "수상", LANGUAGE: "어학", PORTFOLIO: "포트폴리오", PREFERENCE: "병역사항" };
        const entryItems = Object.entries(entryLabels).flatMap(([entryType, label]) => { const matching = entries.filter((entry) => entry.entryType === entryType); return matching.length ? [{ label, count: matching.length, updatedAt: matching.map((entry) => entry.updatedAt).sort().at(-1), details: matching.map(entrySummary) }] : []; });
        const baseSaved = Boolean(profile?.targetRole || profile?.schoolName || profile?.major || profile?.preferredLocations?.length || profile?.technicalSummary || profile?.portfolioUrl);
        setItems([
          ...(baseSaved ? [{ label: "기본 스펙정보", count: 1, details: [[profile?.targetJobFamily, profile?.targetRole].filter(Boolean).join(" · "), profile?.preferredLocations?.length ? `희망 지역: ${profile.preferredLocations.join(", ")}` : null, profile?.availableFrom ? `입사 가능일: ${profile.availableFrom}` : null, profile?.experienceType ? `경력 구분: ${{ ENTRY: "신입", EXPERIENCED: "경력", ANY: "무관" }[profile.experienceType] ?? profile.experienceType}` : null, profile?.technicalSummary ? `기술 요약: ${profile.technicalSummary}` : null, profile?.portfolioUrl ? `포트폴리오: ${profile.portfolioUrl}` : null].filter((value): value is string => Boolean(value)) }] : []),
          ...(skills.length ? [{ label: "보유 기술 스택", count: skills.length, details: skills.map((skill) => skill.skillName) }] : []),
          ...(certificates.length ? [{ label: "자격증", count: certificates.length, details: certificates.map((certificate) => [certificate.name, certificate.issuer, certificate.acquiredAt].filter(Boolean).join(" · ")) }] : []),
          ...entryItems,
          ...(introductions.length ? [{ label: "자기소개서", count: introductions.length, updatedAt: introductions.map((entry) => entry.updatedAt).sort().at(-1), details: introductions.map((entry) => `${entry.title} · ${entry.content.length > 180 ? `${entry.content.slice(0, 180)}…` : entry.content}`) }] : []),
        ]);
      })
      .catch(() => setMessage("저장된 스펙정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, []);
  if (loading) return <p className="resume-document-message">저장된 스펙정보를 불러오는 중입니다.</p>;
  if (message) return <p className="resume-document-message">{message}</p>;
  const stateLabel = saveState.status === "SAVED" ? "저장 완료" : saveState.status === "DRAFT" ? "임시저장" : "저장 전";
  const stateDate = saveState.updatedAt ? new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(saveState.updatedAt)) : "";
  return <div className="saved-capability-list"><div className="saved-capability-list-head"><div><h3>내 스펙정보 목록</h3><p><b className={`save-state-badge ${saveState.status.toLowerCase()}`}>{stateLabel}</b>{stateDate && ` · ${stateDate}`}</p></div>{!readOnly && <button type="button" className="primary-button" onClick={onEdit}>스펙정보 수정</button>}</div>{items.length ? items.map((item) => { const open = expanded === item.label; return <article className={open ? "expanded" : ""} key={item.label}><button type="button" className="saved-capability-summary" onClick={() => setExpanded(open ? null : item.label)} aria-expanded={open}><div><strong>{item.label}</strong><span>{item.count}건 저장됨</span></div><div><small>{item.updatedAt ? `최근 수정 ${new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(item.updatedAt))}` : "저장 이력 없음"}</small>{open ? <ChevronUp size={17} /> : <ChevronDown size={17} />}</div></button>{open && <div className="saved-capability-detail"><ul>{item.details.map((detail, index) => <li key={`${item.label}-${index}`}>{detail}</li>)}</ul>{!readOnly && <button type="button" className="outline-button" onClick={onEdit}>{item.label} 수정하기</button>}</div>}</article>; }) : <p className="empty-state">아직 등록한 스펙정보가 없습니다.</p>}</div>;
}

function ToolHeader({ title, body, close, action }: { title: string; body: string; close: () => void; action?: () => void }) {
  return <div className="capability-open-heading"><div><span className="eyebrow">CAPABILITY WORKFLOW</span><h2>{title}</h2><p>{body}</p></div><div className="form-actions">{action && <button className="primary-button" onClick={action}>이력서 첨부하고 자동 채우기 <FileUp size={15} /></button>}<button className="outline-button" onClick={close}>닫기</button></div></div>;
}

import { useState } from "react";
import { FilePenLine, FileUp, Sparkles, Target } from "lucide-react";
import {
  ResumeProfileAnalysisSection,
  ResumeWritingAssistantSection,
} from "../features/resume/components/ResumeDocumentSection";
import { ProfilePage } from "./ProfilePage";
import { PageHeading } from "../shared/components/PageHeading";

type CapabilityTool = "profile" | "analysis" | "writer" | null;

export function CapabilityManagementPage() {
  const [openTool, setOpenTool] = useState<CapabilityTool>(null);

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
          <button className="outline-button" onClick={() => setOpenTool("profile")}>스펙정보 관리</button>
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

    {openTool === "profile" && <section className="panel capability-open-panel"><ToolHeader title="나의 스펙 정보 입력" body="이력서를 첨부해 자동으로 채우거나, 필요한 항목을 직접 입력해 저장할 수 있습니다." action={() => setOpenTool("analysis")} close={() => setOpenTool(null)} /><ProfilePage /></section>}
    {openTool === "analysis" && <section className="panel capability-open-panel"><ToolHeader title="이력서 첨부하고 자동 채우기" body="PDF/DOCX 이력서에서 발견한 정보만 스펙 제안으로 보여드립니다. 발견되지 않은 항목은 직접 기재해 주세요." close={() => setOpenTool(null)} /><ResumeProfileAnalysisSection /></section>}
    {openTool === "writer" && <section className="panel capability-open-panel"><ToolHeader title="이력서 작성 도우미" body="역량 불러오기 → 양식 선택 → 질문 답변 → AI 초안 생성 순서로 진행합니다." close={() => setOpenTool(null)} /><ResumeWritingAssistantSection /></section>}
    {!openTool && <section className="capability-guide panel"><Sparkles size={20} /><div><strong>기존 이력서가 있다면 먼저 분석해 보세요.</strong><p>추출 결과는 자동 저장되지 않습니다. 확인 후 프로필 반영을 누른 항목만 내 스펙과 채용공고 추천에 사용됩니다.</p></div></section>}
  </>;
}

function ToolHeader({ title, body, close, action }: { title: string; body: string; close: () => void; action?: () => void }) {
  return <div className="capability-open-heading"><div><span className="eyebrow">CAPABILITY WORKFLOW</span><h2>{title}</h2><p>{body}</p></div><div className="form-actions">{action && <button className="primary-button" onClick={action}>이력서 첨부하고 자동 채우기 <FileUp size={15} /></button>}<button className="outline-button" onClick={close}>닫기</button></div></div>;
}

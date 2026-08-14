import { useState } from "react";
import { FilePenLine, FileUp, Sparkles, Target } from "lucide-react";
import { ResumeDocumentSection } from "../features/resume/components/ResumeDocumentSection";
import { ProfilePage } from "./ProfilePage";
import { PageHeading } from "../shared/components/PageHeading";

type CapabilityTool = "profile" | "resume" | null;

export function CapabilityManagementPage() {
  const [openTool, setOpenTool] = useState<CapabilityTool>(null);

  return <>
    <PageHeading
      eyebrow="CAPABILITY MANAGEMENT"
      title="역량 관리"
      body="내 스펙을 정리하고, 이력서에서 정보를 자동 추출하거나 내 역량을 바탕으로 수정 가능한 이력서 초안을 만드세요."
    />

    <section className="capability-tool-grid" aria-label="역량 관리 도구">
      <article className={`capability-tool-card ${openTool === "profile" ? "selected" : ""}`}>
        <span className="capability-tool-icon"><Target size={21} /></span>
        <div><span className="eyebrow">MY CAPABILITY</span><h2>나의 스펙 정보</h2><p>직무, 경력, 기술 스택, 자격증, 학력을 직접 관리합니다.</p></div>
        <div className="capability-tool-actions"><button className="outline-button" onClick={() => setOpenTool("profile")}>스펙 정보 열기</button><button className="primary-button" onClick={() => setOpenTool("resume")}>이력서 첨부하고 자동 채우기 <FileUp size={15} /></button></div>
      </article>
      <article className={`capability-tool-card ${openTool === "resume" ? "selected" : ""}`}>
        <span className="capability-tool-icon"><FilePenLine size={21} /></span>
        <div><span className="eyebrow">RESUME ASSISTANT</span><h2>이력서 작성 도우미</h2><p>저장된 내 역량을 불러오고, 질문 답변과 양식 선택으로 Word 초안을 만듭니다.</p></div>
        <div className="capability-tool-actions"><button className="primary-button" onClick={() => setOpenTool("resume")}>내 역량 불러와 이력서 작성 <Sparkles size={15} /></button></div>
      </article>
    </section>

    {openTool === "profile" && <section className="panel capability-open-panel"><div className="capability-open-heading"><div><span className="eyebrow">SPEC PROFILE</span><h2>나의 스펙 정보</h2><p>직접 수정한 뒤 저장하면 공고 매칭이 다시 계산됩니다.</p></div><button className="outline-button" onClick={() => setOpenTool(null)}>닫기</button></div><ProfilePage /></section>}
    {openTool === "resume" && <section className="panel capability-open-panel"><div className="capability-open-heading"><div><span className="eyebrow">RESUME WORKFLOW</span><h2>이력서 자동 채우기 · 초안 작성</h2><p>PDF/DOCX 이력서를 올려 추출 결과를 프로필에 반영하거나, 저장된 역량을 불러와 양식과 질문 답변으로 Word 초안을 만듭니다.</p></div><button className="outline-button" onClick={() => setOpenTool(null)}>닫기</button></div><ResumeDocumentSection /></section>}
    {!openTool && <section className="capability-guide panel"><Sparkles size={20} /><div><strong>어디서 시작할지 모르겠다면, 이력서를 먼저 올려 보세요.</strong><p>추출된 값은 바로 저장되지 않습니다. 결과를 확인한 뒤 ‘프로필 반영’을 눌러야 내 스펙 정보와 채용공고 추천에 적용됩니다.</p></div></section>}
  </>;
}

import { useEffect, useRef, useState } from "react";
import { CheckCircle2, Download, FileText, MessageSquareText, Sparkles, Upload } from "lucide-react";
import { applyResumeExtraction, extractResumeDocument, generateResumeDocument, listResumeDocuments, type ResumeDocument } from "../api/resumeApi";

const templates = [
  { key: "STANDARD", name: "기본 역량형", description: "핵심 역량·경험·프로젝트를 균형 있게 정리합니다." },
  { key: "PROJECT", name: "프로젝트 강조형", description: "기술 스택과 프로젝트 성과를 먼저 보여줍니다." },
  { key: "COMPACT", name: "간결 경력형", description: "한 페이지 중심의 짧고 선명한 구성을 만듭니다." },
] as const;

const prompts = [
  "지원하려는 직무와 회사 유형은 무엇인가요?",
  "가장 강조하고 싶은 프로젝트 또는 성과는 무엇인가요?",
  "채용 담당자에게 꼭 전달하고 싶은 강점은 무엇인가요?",
];

export function ResumeDocumentSection() {
  const resumeInputRef = useRef<HTMLInputElement>(null);
  const templateInputRef = useRef<HTMLInputElement>(null);
  const [documents, setDocuments] = useState<ResumeDocument[]>([]);
  const [resumeFile, setResumeFile] = useState<File | null>(null);
  const [templateFile, setTemplateFile] = useState<File | null>(null);
  const [templateKey, setTemplateKey] = useState<(typeof templates)[number]["key"]>("STANDARD");
  const [answers, setAnswers] = useState(["", "", ""]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const load = () => void listResumeDocuments().then(setDocuments).catch(() => setMessage("저장된 이력서 자료를 불러오지 못했습니다."));
  useEffect(load, []);

  const upload = async () => {
    if (!resumeFile) { setMessage("분석할 PDF 또는 DOCX 이력서를 선택해 주세요."); return; }
    setLoading(true); setMessage("");
    try {
      const saved = await extractResumeDocument(resumeFile);
      setDocuments((values) => [saved, ...values]);
      setResumeFile(null);
      if (resumeInputRef.current) resumeInputRef.current.value = "";
      setMessage("이력서에서 스펙 후보를 추출했습니다. 제안을 검토한 뒤 프로필에 반영하세요.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "이력서 분석에 실패했습니다."); }
    finally { setLoading(false); }
  };

  const apply = async (id: number) => {
    setLoading(true);
    try { await applyResumeExtraction(id); setMessage("역량 프로필에 반영했고, 공고 매칭을 다시 계산하도록 요청했습니다."); }
    catch (error) { setMessage(error instanceof Error ? error.message : "프로필 반영에 실패했습니다."); }
    finally { setLoading(false); }
  };

  const generate = async () => {
    const additionalRequest = prompts.map((prompt, index) => answers[index].trim() ? `${prompt}\n${answers[index].trim()}` : "").filter(Boolean).join("\n\n");
    setLoading(true); setMessage("");
    try {
      const saved = await generateResumeDocument({ title: "Job-A-Dream 이력서 초안", additionalRequest, templateKey }, templateFile);
      setDocuments((values) => [saved, ...values]);
      setMessage("수정 가능한 Word 이력서 초안을 만들었습니다. 자료 목록에서 내려받아 편집하세요.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "이력서 초안 생성에 실패했습니다."); }
    finally { setLoading(false); }
  };

  return <div className="resume-document-section">
    <div className="resume-document-intro"><div><span className="eyebrow">RESUME INTELLIGENCE</span><h2>이력서를 읽고, 내 스펙으로 다시 완성하세요.</h2><p>PDF/DOCX의 텍스트에서 직무·기술·학력·경력 후보를 찾아 역량 프로필에 제안합니다. 생성 단계에서는 질문 답변과 선택한 양식 구조를 반영해 회원별 편집 가능한 Word 초안을 만듭니다.</p></div><FileText size={34} /></div>

    <div className="resume-document-grid">
      <article className="resume-document-card"><h3><Upload size={18} /> 이력서 업로드 · 스펙 추출</h3><p>PDF 또는 DOCX, 최대 5MB. 원본 파일은 저장하지 않고 추출 결과만 회원별로 저장합니다.</p><input ref={resumeInputRef} type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={(event) => setResumeFile(event.target.files?.[0] ?? null)} /><div className="form-actions"><button className="primary-button" disabled={loading || !resumeFile} onClick={upload}>{loading ? "분석 중..." : "이력서 분석"}</button></div></article>

      <article className="resume-document-card"><h3><MessageSquareText size={18} /> 이력서 작성 질문</h3><p>아래 답변은 생성 초안의 지원 동기·프로젝트·강점 문장에 반영됩니다. 모르는 항목은 비워두고 나중에 Word에서 수정할 수 있어요.</p>{prompts.map((prompt, index) => <label key={prompt} className="resume-question"><span>{index + 1}. {prompt}</span><textarea value={answers[index]} onChange={(event) => setAnswers((current) => current.map((value, answerIndex) => answerIndex === index ? event.target.value : value))} rows={2} placeholder="자유롭게 작성해 주세요." /></label>)}</article>
    </div>

    <section className="resume-template-panel">
      <div><span className="eyebrow">DOCUMENT TEMPLATE</span><h3><Sparkles size={18} /> 이력서 양식을 선택하세요</h3><p>첨부할 양식이 있으면 PDF/DOCX를 넣어 구성 참고용으로 사용합니다. 원본 디자인을 완전히 복제하지는 않으며, 회원 정보로 채운 편집 가능한 DOCX를 생성합니다.</p></div>
      <div className="resume-template-options">{templates.map((template) => <button type="button" key={template.key} className={`resume-template-option ${templateKey === template.key ? "selected" : ""}`} onClick={() => setTemplateKey(template.key)}><CheckCircle2 size={16} /><strong>{template.name}</strong><small>{template.description}</small></button>)}</div>
      <label className="resume-template-upload">첨부할 이력서 양식 (선택) <input ref={templateInputRef} type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={(event) => setTemplateFile(event.target.files?.[0] ?? null)} />{templateFile && <span>{templateFile.name}</span>}</label>
      <button className="primary-button" disabled={loading} onClick={generate}>{loading ? "작성 중..." : "내 이력서 초안 만들기"}</button>
    </section>

    {message && <p className="resume-document-message">{message}</p>}
    <div className="resume-document-list"><h3>내 이력서 자료</h3>{documents.length === 0 ? <p className="empty-state">아직 저장된 이력서 자료가 없습니다.</p> : documents.map((document) => <article key={document.id} className="resume-document-item"><div><span>{document.type === "UPLOADED" ? "업로드 분석" : "생성 초안"}</span><strong>{document.title}</strong><small>{new Intl.DateTimeFormat("ko-KR").format(new Date(document.createdAt))}{document.templateKey && ` · ${templates.find((template) => template.key === document.templateKey)?.name ?? "기본 양식"}`}</small>{document.type === "UPLOADED" && document.extractedProfile && <p>제안 직무: {String(document.extractedProfile.targetRole || "미확인")} · 기술: {(document.extractedProfile.suggestedSkills as string[] | undefined)?.join(", ") || "미확인"}</p>}</div><div className="form-actions">{document.type === "UPLOADED" && <button className="outline-button" disabled={loading} onClick={() => apply(document.id)}>프로필 반영</button>}{document.type === "GENERATED" && <a className="primary-button" href={`/api/v1/members/me/resume-documents/${document.id}/download.docx`}><Download size={15} /> Word 내려받기</a>}</div></article>)}</div>
  </div>;
}

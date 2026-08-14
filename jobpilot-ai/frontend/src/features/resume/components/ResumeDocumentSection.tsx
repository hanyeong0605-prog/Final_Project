import { useEffect, useRef, useState } from "react";
import { CheckCircle2, ChevronRight, Download, FileText, MessageSquareText, Sparkles, Upload } from "lucide-react";
import { applyResumeExtraction, deleteResumeDocument, extractResumeDocument, generateResumeDocument, listResumeDocuments, type ResumeDocument } from "../api/resumeApi";

const templates = [
  { key: "STANDARD", name: "기본 역량형", description: "학력·역량·경력·프로젝트를 균형 있게 정리합니다." },
  { key: "PROJECT", name: "프로젝트 강조형", description: "기술 스택과 프로젝트 성과를 먼저 보여줍니다." },
  { key: "COMPACT", name: "간결 경력형", description: "한 페이지 중심으로 핵심만 간결하게 구성합니다." },
] as const;

const questions = [
  "성장과정 및 성격: 지금의 나를 만든 경험과 강점을 들려주세요.",
  "내가 잘할 수 있는 일: 가장 자신 있는 역할과 그 이유를 알려주세요.",
  "습득기술 및 직무관련 역량: 실제로 사용한 기술과 프로젝트 경험을 적어주세요.",
  "회사 업무에 대한 자세 및 포부: 지원 후 어떤 기여를 하고 싶은지 알려주세요.",
];

type Profile = Record<string, unknown>;
const profileFields: Array<{ key: string; label: string; format?: (value: unknown) => string }> = [
  { key: "targetRole", label: "희망 직무" },
  { key: "suggestedSkills", label: "보유 기술 스택", format: (value) => Array.isArray(value) ? value.join(", ") : "" },
  { key: "suggestedCertificates", label: "보유 자격증", format: (value) => Array.isArray(value) ? value.join(", ") : "" },
  { key: "educationLevel", label: "학력" },
  { key: "schoolName", label: "학교명" },
  { key: "major", label: "전공" },
  { key: "graduationStatus", label: "졸업 상태" },
  { key: "totalCareerMonths", label: "경력", format: (value) => Number(value) > 0 ? `${value}개월` : "" },
  { key: "technicalSummary", label: "기술·경험 요약" },
];

function useDocuments() {
  const [documents, setDocuments] = useState<ResumeDocument[]>([]);
  const [message, setMessage] = useState("");
  const load = () => void listResumeDocuments().then(setDocuments).catch(() => setMessage("내 이력서 자료를 불러오지 못했습니다."));
  useEffect(load, []);
  return { documents, setDocuments, message, setMessage, load };
}

export function ResumeProfileAnalysisSection() {
  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [selected, setSelected] = useState<ResumeDocument | null>(null);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(false);
  const { documents, setDocuments, message, setMessage } = useDocuments();

  const analyze = async () => {
    if (!file) return setMessage("분석할 PDF 또는 DOCX 이력서를 선택해 주세요.");
    setLoading(true); setMessage("");
    try {
      const saved = await extractResumeDocument(file);
      setDocuments((current) => [saved, ...current]); setSelected(saved);
      setFile(null); if (fileRef.current) fileRef.current.value = "";
      setMessage("이력서에서 찾은 스펙 정보를 확인하세요. 프로필 반영 전에는 저장되지 않습니다.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "이력서 분석에 실패했습니다."); }
    finally { setLoading(false); }
  };
  const apply = async (id: number) => {
    setLoading(true); setMessage("");
    try { await applyResumeExtraction(id); setMessage("발견된 정보를 내 스펙에 반영했습니다. 채용공고 매칭도 다시 계산됩니다."); }
    catch (error) { setMessage(error instanceof Error ? error.message : "프로필 반영에 실패했습니다."); }
    finally { setLoading(false); }
  };
  const remove = async (ids: number[]) => {
    if (ids.length === 0 || !confirm(`선택한 이력서 ${ids.length}개를 삭제할까요?`)) return;
    setLoading(true); setMessage("");
    try {
      await Promise.all(ids.map(deleteResumeDocument));
      setDocuments((current) => current.filter((document) => !ids.includes(document.id)));
      if (selected && ids.includes(selected.id)) setSelected(null);
      setSelectedIds((current) => current.filter((id) => !ids.includes(id)));
      setMessage("선택한 이력서 자료를 삭제했습니다.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "이력서 자료 삭제에 실패했습니다."); }
    finally { setLoading(false); }
  };
  const uploaded = documents.filter((document) => document.type === "UPLOADED");
  const extracted = (selected?.extractedProfile ?? {}) as Profile;
  return <div className="resume-document-section">
    <section className="resume-analysis-hero"><div><span className="eyebrow">RESUME ANALYSIS</span><h2>이력서를 읽고, 내 스펙 제안만 받아보세요.</h2><p>질문이나 이력서 양식 선택은 하지 않습니다. 업로드한 이력서 안에서 실제로 발견한 정보만 보여드립니다.</p></div><FileText size={36} /></section>
    <section className="resume-document-card resume-upload-card"><h3><Upload size={18} /> 이력서 첨부</h3><p>PDF 또는 DOCX, 최대 5MB. 이력서 텍스트에서 희망 직무·기술·자격증·학력·경력을 찾아 제안합니다.</p><input ref={fileRef} type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={(event) => setFile(event.target.files?.[0] ?? null)} /><button className="primary-button" disabled={loading || !file} onClick={analyze}>{loading ? "분석 중…" : "이력서 분석"}</button></section>
    {selected && <section className="resume-extracted-result"><div className="resume-result-heading"><div><span className="eyebrow">EXTRACTED PROFILE</span><h3>이력서에서 찾은 스펙 정보</h3></div><button className="primary-button" disabled={loading} onClick={() => apply(selected.id)}>프로필에 반영</button></div><div className="resume-profile-suggestions">{profileFields.map((field) => { const raw = extracted[field.key]; const value = field.format ? field.format(raw) : String(raw ?? ""); const found = Boolean(value && value !== "0"); return <article key={field.key} className={found ? "found" : "missing"}><strong>{field.label}</strong>{found ? <p>{value}</p> : <p>이력서에서 발견되지 않아 직접 기재해주세요!</p>}</article>; })}</div></section>}
    {message && <p className="resume-document-message">{message}</p>}
    <section className="resume-document-list"><div className="resume-result-heading"><h3>내 이력서 자료</h3>{selectedIds.length > 0 && <button className="outline-button" disabled={loading} onClick={() => void remove(selectedIds)}>선택 삭제 ({selectedIds.length})</button>}</div>{uploaded.length === 0 ? <p className="empty-state">아직 분석한 이력서 자료가 없습니다.</p> : uploaded.map((document) => <article key={document.id} className="resume-document-item"><input aria-label={`${document.title} 선택`} type="checkbox" checked={selectedIds.includes(document.id)} onChange={() => setSelectedIds((current) => current.includes(document.id) ? current.filter((id) => id !== document.id) : [...current, document.id])} /><div><span>업로드 분석</span><strong>{document.title}</strong><small>{new Intl.DateTimeFormat("ko-KR").format(new Date(document.createdAt))}</small></div><div className="form-actions"><button className="outline-button" onClick={() => setSelected(document)}>분석 결과 보기</button><button className="outline-button" disabled={loading} onClick={() => apply(document.id)}>프로필 반영</button><button className="outline-button" disabled={loading} onClick={() => void remove([document.id])}>삭제</button></div></article>)}</section>
  </div>;
}

export function ResumeWritingAssistantSection() {
  const templateRef = useRef<HTMLInputElement>(null);
  const [step, setStep] = useState<1 | 2 | 3 | 4>(1);
  const [enabled, setEnabled] = useState({ profile: true, skills: true, certificates: true, education: true, projects: true });
  const [templateKey, setTemplateKey] = useState<(typeof templates)[number]["key"]>("STANDARD");
  const [templateFile, setTemplateFile] = useState<File | null>(null);
  const [answers, setAnswers] = useState<string[]>(["", "", "", ""]);
  const [loading, setLoading] = useState(false);
  const { documents, setDocuments, message, setMessage } = useDocuments();
  const generate = async () => {
    const additionalRequest = questions.map((question, index) => answers[index].trim() ? `[${question}]\n${answers[index].trim()}` : "").filter(Boolean).join("\n\n");
    if (!additionalRequest) return setMessage("이력서 작성을 위한 질문에 한 가지 이상 답해 주세요.");
    setLoading(true); setMessage("");
    try { const enabledSections = Object.entries(enabled).filter(([, active]) => active).map(([key]) => key); const saved = await generateResumeDocument({ title: "Job-A-Dream 이력서 초안", additionalRequest, templateKey, answers, enabledSections }, templateFile); setDocuments((current) => [saved, ...current]); setStep(4); setMessage("수정 가능한 Word 이력서 초안을 만들었습니다. 내 이력서 자료에서 내려받을 수 있어요."); }
    catch (error) { setMessage(error instanceof Error ? error.message : "이력서 초안 생성에 실패했습니다."); }
    finally { setLoading(false); }
  };
  const generated = documents.filter((document) => document.type === "GENERATED");
  return <div className="resume-document-section">
    <section className="resume-analysis-hero"><div><span className="eyebrow">RESUME ASSISTANT</span><h2>내 역량을 바탕으로 이력서 초안을 작성하세요.</h2><p>내 역량 불러오기, 양식 선택, 질문 답변을 거쳐 AI가 사실에 기반한 수정 가능한 Word 초안을 만듭니다.</p></div><Sparkles size={36} /></section>
    <ol className="resume-stepper"><li className={step >= 1 ? "active" : ""}>1. 역량 불러오기</li><li className={step >= 2 ? "active" : ""}>2. 양식 선택</li><li className={step >= 3 ? "active" : ""}>3. 질문 답변</li><li className={step >= 4 ? "active" : ""}>4. 초안 생성</li></ol>
    {step === 1 && <section className="resume-document-card"><h3>내 역량 불러오기</h3><p>초안에 쓸 항목을 켜거나 끌 수 있습니다. 실제 저장된 역량은 다음 단계에서 AI 작성 재료로 사용됩니다.</p><div className="resume-capability-toggles">{Object.entries({ profile: "희망 직무·경력", skills: "보유 기술 스택", certificates: "보유 자격증", education: "학력", projects: "프로젝트·자기소개" }).map(([key, label]) => <label key={key}><input type="checkbox" checked={enabled[key as keyof typeof enabled]} onChange={() => setEnabled((current) => ({ ...current, [key]: !current[key as keyof typeof enabled] }))} />{label}</label>)}</div><button className="primary-button" onClick={() => setStep(2)}>양식 선택으로 <ChevronRight size={15} /></button></section>}
    {step === 2 && <section className="resume-template-panel"><div><span className="eyebrow">DOCUMENT TEMPLATE</span><h3>이력서 양식을 선택해 주세요</h3><p>직접 가진 PDF/DOCX 양식을 첨부하거나, 기본 양식 중 하나를 선택할 수 있습니다.</p></div><div className="resume-template-options">{templates.map((template) => <button type="button" key={template.key} className={`resume-template-option ${templateKey === template.key ? "selected" : ""}`} onClick={() => setTemplateKey(template.key)}><CheckCircle2 size={16} /><strong>{template.name}</strong><small>{template.description}</small></button>)}</div><label className="resume-template-upload">첨부할 이력서 양식 (선택)<input ref={templateRef} type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={(event) => setTemplateFile(event.target.files?.[0] ?? null)} />{templateFile && <span>{templateFile.name}</span>}</label><div className="form-actions"><button className="outline-button" onClick={() => setStep(1)}>이전</button><button className="primary-button" onClick={() => setStep(3)}>질문 시작 <ChevronRight size={15} /></button></div></section>}
    {step === 3 && <section className="resume-document-card"><h3><MessageSquareText size={18} /> 이력서 작성을 위한 질문</h3><p>답변에 없는 경험이나 성과는 만들어내지 않고, 적어주신 정보와 선택한 역량을 바탕으로 초안을 작성합니다.</p>{questions.map((question, index) => <label key={question} className="resume-question"><span>{index + 1}. {question}</span><textarea rows={3} value={answers[index]} onChange={(event) => setAnswers((current) => current.map((value, answerIndex) => answerIndex === index ? event.target.value : value))} placeholder="자유롭게 작성해 주세요." /></label>)}<div className="form-actions"><button className="outline-button" onClick={() => setStep(2)}>이전</button><button className="primary-button" disabled={loading} onClick={generate}>{loading ? "AI 초안 작성 중…" : "내 이력서 초안 만들기"}</button></div></section>}
    {message && <p className="resume-document-message">{message}</p>}
    {generated.length > 0 && <section className="resume-document-list"><h3>생성한 이력서 초안</h3>{generated.map((document) => <article key={document.id} className="resume-document-item"><div><span>AI 생성 초안</span><strong>{document.title}</strong><small>{new Intl.DateTimeFormat("ko-KR").format(new Date(document.createdAt))}</small></div><a className="primary-button" href={`/api/v1/members/me/resume-documents/${document.id}/download.docx`}><Download size={15} /> Word 내려받기</a></article>)}</section>}
  </div>;
}

// 기존 /resume 화면과의 호환성을 유지한다. 새 역량 관리 화면에서는 두 흐름을 분리해 사용한다.
export function ResumeDocumentSection() { return <ResumeWritingAssistantSection />; }

import { useEffect, useRef, useState } from "react";
import { CheckCircle2, ChevronRight, Download, FileText, LoaderCircle, Sparkles, Upload } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { applyResumeExtraction, deleteResumeDocument, downloadResumeDocument, extractResumeDocument, generateResumeDocument, getResumeDraftContext, listResumeDocuments, type ResumeDocument, type ResumeDraftContext } from "../api/resumeApi";
import { getResumeAiConsent, saveResumeAiConsent } from "../api/resumeConsentApi";

const templates = [
  { key: "ACADEMY", name: "개발교육원형", description: "교육·수행 프로젝트·기술 역량을 자세히 보여줍니다.", preview: "/resume-templates/academy.png" },
  { key: "SARAMIN", name: "사람인형", description: "기본사항과 학력·경력 중심으로 정리합니다.", preview: "/resume-templates/saramin.png" },
  { key: "JOBKOREA", name: "잡코리아형", description: "핵심 이력을 간결한 표 형식으로 정리합니다.", preview: "/resume-templates/jobkorea.png" },
] as const;

type Profile = Record<string, unknown>;
const personalInfo = (value: unknown) => {
  if (!value || typeof value !== "object") return "";
  const fields = value as Record<string, unknown>;
  return [["성명", fields.name], ["한자", fields.hanjaName], ["생년월일", fields.birthDate], ["E-mail", fields.email], ["휴대전화", fields.phone]]
    .filter(([, item]) => typeof item === "string" && item.trim()).map(([label, item]) => `${label}: ${item}`).join(" · ");
};
const profileFields: Array<{ key: string; label: string; format?: (value: unknown) => string }> = [
  { key: "personalInfo", label: "인적사항", format: personalInfo },
  { key: "targetRole", label: "희망 직무" },
  { key: "suggestedSkills", label: "보유 기술 스택", format: (value) => Array.isArray(value) ? value.join(", ") : "" },
  { key: "suggestedCertificates", label: "보유 자격증", format: (value) => Array.isArray(value) ? value.join(", ") : "" },
  { key: "educationLevel", label: "학력" },
  { key: "schoolName", label: "학교명" },
  { key: "major", label: "전공" },
  { key: "graduationStatus", label: "졸업 상태" },
  { key: "totalCareerMonths", label: "경력", format: (value) => Number(value) > 0 ? `${value}개월` : "" },
];
const detailedProfileFields: Array<{ label: string; value: (profile: Profile) => string }> = [
  { label: "학력", value: (profile) => itemNames(profile.educations, "school") || [profile.schoolName, profile.major, profile.educationLevel, profile.graduationStatus].filter(Boolean).join(" · ") },
  { label: "관련 경력", value: (profile) => relevantCareerNames(profile.careers) || (Number(profile.totalCareerMonths) > 0 ? `${profile.totalCareerMonths}개월` : "") },
  { label: "인턴 · 대외활동", value: () => "" }, { label: "교육이수", value: (profile) => itemNames(profile.trainings, "title") },
  { label: "자격증", value: (profile) => itemNames(profile.certificateDetails, "name") || (Array.isArray(profile.suggestedCertificates) ? profile.suggestedCertificates.join(", ") : "") },
  { label: "수상", value: (profile) => itemNames(profile.awards, "title") }, { label: "해외경험", value: () => "" }, { label: "어학", value: () => "" },
  { label: "포트폴리오", value: (profile) => itemNames(profile.portfolios, "title") }, { label: "병역사항", value: (profile) => militaryText(profile.militaryService) },
  { label: "자기소개서", value: (profile) => itemNames(profile.selfIntroductions, "title") },
];
const itemNames = (value: unknown, key: string) => Array.isArray(value) ? value.map((item) => item && typeof item === "object" ? String((item as Record<string, unknown>)[key] ?? "") : "").filter(Boolean).join(" · ") : "";
const relevantCareerNames = (value: unknown) => Array.isArray(value) ? value.filter((item) => item && typeof item === "object" && (item as Record<string, unknown>).relevantCareer === true).map((item) => String((item as Record<string, unknown>).company ?? "")).filter(Boolean).join(" · ") : "";
const militaryText = (value: unknown) => value && typeof value === "object" ? ["serviceType", "branch", "rank"].map((key) => String((value as Record<string, unknown>)[key] ?? "")).filter(Boolean).join(" · ") : "";

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
  const [aiConsent, setAiConsent] = useState(false);
  const { documents, setDocuments, message, setMessage } = useDocuments();
  useEffect(() => { void getResumeAiConsent().then((value) => setAiConsent(value.agreed)).catch(() => undefined); }, []);

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
  const setConsent = async (agreed: boolean) => { try { const saved = await saveResumeAiConsent(agreed); setAiConsent(saved.agreed); } catch (error) { setMessage(error instanceof Error ? error.message : "동의 상태를 저장하지 못했습니다."); } };
  return <div className="resume-document-section">
    <section className="resume-analysis-hero"><div><span className="eyebrow">RESUME ANALYSIS</span><h2>이력서를 읽고, 내 스펙 제안만 받아보세요.</h2><p>질문이나 이력서 양식 선택은 하지 않습니다. 업로드한 이력서 안에서 실제로 발견한 정보만 보여드립니다.</p></div><FileText size={36} /></section>
    <section className="resume-document-card resume-upload-card"><h3><Upload size={18} /> 이력서 첨부</h3><p>PDF 또는 DOCX, 최대 5MB. DOCX 표는 행 단위로 읽어 학력·경력·교육이수·수상·프로젝트·자격증·병역·자기소개서 항목을 제안합니다.</p><label className="resume-consent"><input type="checkbox" checked={aiConsent} onChange={(event) => void setConsent(event.target.checked)} /> 이력서의 민감 정보를 AI 분석에 전송하여 구조화·초안 생성에 사용하는 것에 동의합니다. 동의하지 않아도 로컬 텍스트 추출은 가능합니다.</label><input ref={fileRef} type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={(event) => setFile(event.target.files?.[0] ?? null)} /><button className="primary-button" disabled={loading || !file} onClick={analyze}>{loading ? "분석 중…" : "이력서 분석"}</button></section>
    {selected && <section className="resume-extracted-result"><div className="resume-result-heading"><div><span className="eyebrow">EXTRACTED PROFILE</span><h3>이력서에서 찾은 스펙 정보</h3></div><button className="primary-button" disabled={loading} onClick={() => apply(selected.id)}>프로필에 반영</button></div><div className="resume-profile-suggestions">{[...profileFields, ...detailedProfileFields.map((field) => ({ key: field.label, label: field.label, format: () => field.value(extracted) }))].map((field) => { const raw = extracted[field.key]; const value = field.format ? field.format(raw) : String(raw ?? ""); const found = Boolean(value && value !== "0"); return <article key={field.key} className={found ? "found" : "missing"}><strong>{field.label}</strong>{found ? <p>{value}</p> : <p>이력서에서 발견되지 않았습니다. 직접 입력해 주세요.</p>}</article>; })}</div></section>}
    {message && <p className="resume-document-message">{message}</p>}
    <section className="resume-document-list"><div className="resume-result-heading"><h3>내 이력서 자료</h3>{selectedIds.length > 0 && <button className="outline-button" disabled={loading} onClick={() => void remove(selectedIds)}>선택 삭제 ({selectedIds.length})</button>}</div>{uploaded.length === 0 ? <p className="empty-state">아직 분석한 이력서 자료가 없습니다.</p> : uploaded.map((document) => <article key={document.id} className="resume-document-item"><input aria-label={`${document.title} 선택`} type="checkbox" checked={selectedIds.includes(document.id)} onChange={() => setSelectedIds((current) => current.includes(document.id) ? current.filter((id) => id !== document.id) : [...current, document.id])} /><div><span>업로드 분석</span><strong>{document.title}</strong><small>{new Intl.DateTimeFormat("ko-KR").format(new Date(document.createdAt))}</small></div><div className="form-actions"><button className="outline-button" onClick={() => setSelected(document)}>분석 결과 보기</button><button className="outline-button" disabled={loading} onClick={() => apply(document.id)}>프로필 반영</button><button className="outline-button" disabled={loading} onClick={() => void remove([document.id])}>삭제</button></div></article>)}</section>
  </div>;
}

export function ResumeWritingAssistantSection() {
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [enabled, setEnabled] = useState({ profile: true, skills: true, certificates: true, education: true, projects: true });
  const [templateKey, setTemplateKey] = useState<(typeof templates)[number]["key"]>("ACADEMY");
  const [loading, setLoading] = useState(false);
  const [loadingCapabilities, setLoadingCapabilities] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [selectedGeneratedIds, setSelectedGeneratedIds] = useState<number[]>([]);
  const [context, setContext] = useState<ResumeDraftContext | null>(null);
  const [aiConsent, setAiConsent] = useState(false);
  const { documents, setDocuments, message, setMessage } = useDocuments();
  const navigate = useNavigate();
  useEffect(() => { void getResumeAiConsent().then((value) => setAiConsent(value.agreed)).catch(() => undefined); }, []);
  const loadCapabilities = async () => {
    setLoadingCapabilities(true); setMessage("");
    try { setContext(await getResumeDraftContext()); }
    catch (error) { setMessage(error instanceof Error ? error.message : "저장된 스펙 정보를 불러오지 못했습니다."); }
    finally { setLoadingCapabilities(false); }
  };
  const moveToTemplates = () => {
    if (!context) { void loadCapabilities(); return; }
    if (!Object.values(enabled).some(Boolean)) { setMessage("초안에 사용할 역량 항목을 하나 이상 선택해 주세요."); return; }
    setShowConfirm(true);
  };
  const generate = async () => {
    if (!aiConsent) return setMessage("AI 초안 생성 전 이력서 정보의 AI 처리 동의가 필요합니다.");
    setLoading(true); setMessage("");
    try { const enabledSections = Object.entries(enabled).filter(([, active]) => active).map(([key]) => key); const saved = await generateResumeDocument({ title: "Job-A-Dream 이력서 초안", additionalRequest: "", templateKey, answers: ["저장된 이력·프로젝트 문장에 있는 사실만 사용하고, 비어 있는 정보는 추측하지 마세요."], enabledSections }); setDocuments((current) => [saved, ...current]); setStep(3); setMessage("수정 가능한 Word 이력서 초안을 만들었습니다. 양식 원본과 AI 초안 모두 Word에서 바로 수정할 수 있습니다."); }
    catch (error) { setMessage(error instanceof Error ? error.message : "이력서 초안 생성에 실패했습니다."); }
    finally { setLoading(false); }
  };
  const generated = documents.filter((document) => document.type === "GENERATED");
  const removeGenerated = async (ids: number[]) => {
    if (!ids.length || !confirm(`선택한 이력서 초안 ${ids.length}개를 삭제할까요? 삭제 후 복구할 수 없습니다.`)) return;
    setLoading(true); setMessage("");
    try { await Promise.all(ids.map(deleteResumeDocument)); setDocuments((current) => current.filter((document) => !ids.includes(document.id))); setSelectedGeneratedIds((current) => current.filter((id) => !ids.includes(id))); setMessage("선택한 이력서 초안을 삭제했습니다."); }
    catch (error) { setMessage(error instanceof Error ? error.message : "이력서 초안을 삭제하지 못했습니다."); }
    finally { setLoading(false); }
  };
  return <div className="resume-document-section">
    <section className="resume-analysis-hero"><div><span className="eyebrow">RESUME ASSISTANT</span><h2>내 역량을 바탕으로 이력서 초안을 작성하세요.</h2><p>저장된 이력·기술·프로젝트의 실제 문장만 불러온 뒤, 선택한 양식에 맞는 수정 가능한 Word 초안을 만듭니다.</p></div><Sparkles size={36} /></section>
    <ol className="resume-stepper"><li className={step >= 1 ? "active" : ""}>1. 역량 불러오기</li><li className={step >= 2 ? "active" : ""}>2. 양식 선택</li><li className={step >= 3 ? "active" : ""}>3. 초안 생성</li></ol>
    {step === 1 && <section className="resume-document-card"><div className="resume-result-heading"><div><h3>내 역량 불러오기</h3><p>체크한 항목만 이력서 초안에 사용합니다. 체크를 해제하면 해당 정보는 AI에 전달하지 않습니다.</p></div><button className="primary-button" disabled={loadingCapabilities} onClick={() => void loadCapabilities()}>{loadingCapabilities ? <><LoaderCircle className="spinning" size={15} /> 불러오는 중…</> : "내 역량 불러오기"}</button></div>{loadingCapabilities && <div className="resume-loading"><LoaderCircle className="spinning" size={20} /><strong>내 스펙 정보를 불러오는 중입니다…</strong><span>저장된 이력과 프로젝트를 확인하고 있어요.</span></div>}{context && <details className="resume-capability-preview" open><summary>불러온 내 역량 보기 · 열기/닫기</summary>{Object.entries({ profile: "희망 직무·경력", skills: "보유 기술 스택", certificates: "보유 자격증", education: "학력·경력 이력", projects: "프로젝트·자기소개" }).map(([key, label]) => <article key={key}><label className="resume-capability-toggle"><input type="checkbox" checked={enabled[key as keyof typeof enabled]} onChange={() => setEnabled((current) => ({ ...current, [key]: !current[key as keyof typeof enabled] }))} /><span><strong>{label}</strong><small>초안에 포함</small></span></label><p>{(context[key as keyof ResumeDraftContext] ?? []).length ? (context[key as keyof ResumeDraftContext] ?? []).join("\n") : "저장된 정보가 없습니다."}</p></article>)}</details>}<div className="form-actions"><button className="outline-button" onClick={() => navigate("/capability?tool=profile")}>역량 수정하기</button><button className="primary-button" disabled={loadingCapabilities || !context} onClick={moveToTemplates}>양식 선택으로 <ChevronRight size={15} /></button></div></section>}
    {step === 2 && <section className="resume-template-panel"><div><span className="eyebrow">DOCUMENT TEMPLATE</span><h3>이력서 양식을 선택해 주세요</h3><p>올려주신 빈 양식의 첫 페이지 미리보기입니다. 생성본은 선택 양식을 포함한 편집 가능한 Word 파일입니다.</p></div><div className="resume-template-options">{templates.map((template) => <button type="button" key={template.key} className={`resume-template-option ${templateKey === template.key ? "selected" : ""}`} onClick={() => setTemplateKey(template.key)}><img src={template.preview} alt={`${template.name} 빈 양식 미리보기`} /><div><CheckCircle2 size={16} /><strong>{template.name}</strong><small>{template.description}</small></div></button>)}</div><label className="resume-consent"><input type="checkbox" checked={aiConsent} onChange={async (event) => { try { const saved = await saveResumeAiConsent(event.target.checked); setAiConsent(saved.agreed); } catch (error) { setMessage(error instanceof Error ? error.message : "동의 상태를 저장하지 못했습니다."); } }} /> 이력서에 입력한 개인정보와 경력 정보를 AI 초안 생성에 전송하는 것에 동의합니다.</label><div className="form-actions"><button className="outline-button" onClick={() => setStep(1)}>이전</button><button className="primary-button" disabled={loading} onClick={generate}>{loading ? <><LoaderCircle className="spinning" size={15} /> 초안을 작성 중입니다…</> : "내 이력서 초안 만들기"}</button></div>{loading && <div className="resume-loading"><LoaderCircle className="spinning" size={20} /><strong>초안을 작성 중입니다…</strong><span>선택한 양식과 저장한 역량을 연결하고 있어요.</span></div>}</section>}
    {message && <p className="resume-document-message">{message}</p>}
    {generated.length > 0 && <section className="resume-document-list"><div className="resume-result-heading"><div><h3>생성한 이력서 초안</h3><p>생성된 초안은 회원별 DB에 저장됩니다.</p></div>{selectedGeneratedIds.length > 0 && <button className="outline-button" disabled={loading} onClick={() => void removeGenerated(selectedGeneratedIds)}>선택 삭제 ({selectedGeneratedIds.length})</button>}</div>{generated.map((document) => <article key={document.id} className="resume-document-item"><input aria-label={`${document.title} 선택`} type="checkbox" checked={selectedGeneratedIds.includes(document.id)} onChange={() => setSelectedGeneratedIds((current) => current.includes(document.id) ? current.filter((id) => id !== document.id) : [...current, document.id])} /><div><span>AI 생성 초안</span><strong>{document.title}</strong><small>{new Intl.DateTimeFormat("ko-KR").format(new Date(document.createdAt))}</small></div><div className="form-actions"><button className="outline-button" disabled={loading} onClick={() => void removeGenerated([document.id])}>삭제</button><button className="primary-button" onClick={() => void downloadResumeDocument(document.id).catch((error) => setMessage(error instanceof Error ? error.message : "Word 파일을 내려받지 못했습니다."))}><Download size={15} /> Word 내려받기</button></div></article>)}</section>}
    {showConfirm && <div className="resume-confirm-backdrop" role="dialog" aria-modal="true"><section className="resume-confirm-modal"><Sparkles size={24} /><h3>이 내용으로 이력서 초안을 작성할까요?</h3><p>선택한 저장 역량과 실제 이력 문장을 바탕으로 작성합니다. 저장되지 않은 사실은 새로 만들지 않습니다.</p><div className="form-actions"><button className="outline-button" onClick={() => setShowConfirm(false)}>아니요</button><button className="primary-button" onClick={() => { setShowConfirm(false); setStep(2); }}>예, 양식 선택하기</button></div></section></div>}
  </div>;
}

// 기존 /resume 화면과의 호환성을 유지한다. 새 역량 관리 화면에서는 두 흐름을 분리해 사용한다.
export function ResumeDocumentSection() { return <ResumeWritingAssistantSection />; }

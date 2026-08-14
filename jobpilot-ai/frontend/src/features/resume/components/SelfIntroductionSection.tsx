import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { Send, Upload } from "lucide-react";
import {
  applyResumeExtraction,
  createSelfIntroduction,
  deleteSelfIntroduction,
  extractResumeDocument,
  listSelfIntroductions,
  updateSelfIntroduction,
} from "../api/resumeApi";
import {
  critiqueSelfIntroduction,
  fetchSelfIntroductionQuestions,
  generateSelfIntroductionDraft,
  parseCompanyQuestions,
} from "../api/resumeAiApi";
import type { SelfIntroduction, SelfIntroductionCritiqueResult } from "../model/resume.types";

// 2026-08-10: 자기소개서 작성/관리 섹션 - 태스크 #61. "질문식으로 작성" vs "직접 쓰고
// 첨삭받기" 둘 다 지원한다(사용자 요청). 생성(ai-server)과 저장(백엔드 CRUD)이 분리돼
// 있어서 - 여기서 그 둘을 순서대로 조합한다: 생성 결과를 그대로 저장하지 않고, 사용자가
// 미리보기에서 한 번 더 수정할 수 있게 편집 가능한 textarea에 채워준다(AI가 지어낸 내용을
// 그대로 믿지 않고 검토하게 하려는 의도).
//
// 2026-08-13: "질문식으로 작성"을 정적인 질문 목록 폼 대신, 사이트 챗봇(SiteAssistantWidget)과
// 같은 실시간 채팅 형태로 바꿨다 - 마스코트(고양이, mascot-code.png)가 한 번에 질문 하나씩
// 말풍선으로 묻고, 답하면 다음 질문으로 넘어간다. 채팅 시작 전에 회사 자소서 양식(채용
// 페이지에서 복사한 문항 텍스트)을 붙여넣으면 그 문항 기준으로, 안 붙여넣으면 기존 기본
// 4문항(GUIDED_QUESTIONS) 기준으로 질문한다(parse_company_questions 참고).
interface Props {
  job: string;
  techSummary: string;
}

type EditorMode = "guided" | "freeform" | null;
type ChatPhase = "setup" | "chatting" | "done";
type ChatMessage = { id: string; role: "bot" | "user"; text: string };

let selfIntroChatIdCounter = 0;
function nextChatId(): string {
  selfIntroChatIdCounter += 1;
  return `self-intro-chat-${selfIntroChatIdCounter}`;
}

export function SelfIntroductionSection({ job, techSummary }: Props) {
  const [entries, setEntries] = useState<SelfIntroduction[]>([]);
  const [loading, setLoading] = useState(true);
  const [questions, setQuestions] = useState<string[]>([]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [editorMode, setEditorMode] = useState<EditorMode>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [title, setTitle] = useState("");
  const [primary, setPrimary] = useState(false);
  const [answers, setAnswers] = useState<string[]>([]);
  const [content, setContent] = useState("");
  const [critique, setCritique] = useState<SelfIntroductionCritiqueResult | null>(null);
  const [busy, setBusy] = useState(false);

  // 2026-08-13: 질문식(guided) 채팅 전용 상태 - 위 answers/content/busy는 채팅이 끝난 뒤
  // (chatPhase === "done") 결과 미리보기/저장 단계에서 그대로 재사용한다.
  const [chatPhase, setChatPhase] = useState<ChatPhase>("setup");
  const [companyFormatText, setCompanyFormatText] = useState("");
  const [parsingCompanyFormat, setParsingCompanyFormat] = useState(false);
  const [companyFormatError, setCompanyFormatError] = useState<string | null>(null);
  const [activeQuestions, setActiveQuestions] = useState<string[]>([]);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatInput, setChatInput] = useState("");
  const [chatQuestionIndex, setChatQuestionIndex] = useState(0);
  const chatLogRef = useRef<HTMLDivElement>(null);

  // 2026-08-14: "이력서 파일을 미리 들고 오면 스캔해서 채울 수 있는 건 채우고, 나머지는
  // 채팅으로 물어보기" 요청 - 기존 이력서 작성 도우미 페이지의 업로드/추출/프로필반영
  // API(ResumeDocumentSection과 동일한 엔드포인트)를 그대로 재사용한다. 질문 자체(지원동기,
  // 강점/약점 등 서술형)는 이력서 텍스트만으로 채울 수 없어서 그대로 채팅으로 묻고, 추출된
  // 텍스트는 resumeContext로 저장해뒀다가 "기본 질문으로 시작"/"양식으로 질문 만들기" 둘 중
  // 뭘 누르든 함께 넘겨서(finishChatAndGenerate) 답변을 다듬을 때 배경 참고자료로만 쓰게 한다.
  const [resumeContext, setResumeContext] = useState("");
  const [resumeUploading, setResumeUploading] = useState(false);
  const [resumeUploadError, setResumeUploadError] = useState<string | null>(null);
  const [resumeUploadInfo, setResumeUploadInfo] = useState<{ id: number; targetRole: string; skills: string[] } | null>(null);
  const [applyingResumeProfile, setApplyingResumeProfile] = useState(false);
  const resumeFileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    chatLogRef.current?.scrollTo({ top: chatLogRef.current.scrollHeight, behavior: "smooth" });
  }, [chatMessages, busy]);

  useEffect(() => {
    void Promise.all([listSelfIntroductions(), fetchSelfIntroductionQuestions()])
      .then(([list, q]) => { setEntries(list); setQuestions(q.questions); setAnswers(q.questions.map(() => "")); })
      .catch(() => setErrorMessage("자기소개서 목록을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, []);

  const resetEditor = () => {
    setEditorMode(null); setEditingId(null); setTitle(""); setPrimary(false);
    setAnswers(questions.map(() => "")); setContent(""); setCritique(null); setErrorMessage(null);
    setChatPhase("setup"); setCompanyFormatText(""); setCompanyFormatError(null);
    setActiveQuestions([]); setChatMessages([]); setChatInput(""); setChatQuestionIndex(0);
    setResumeContext(""); setResumeUploadError(null); setResumeUploadInfo(null);
  };

  const handleResumeFileSelected = async (file: File) => {
    setResumeUploading(true);
    setResumeUploadError(null);
    try {
      const document = await extractResumeDocument(file);
      setResumeContext(document.extractedText ?? "");
      const profile = document.extractedProfile ?? {};
      const skills = Array.isArray(profile.suggestedSkills) ? (profile.suggestedSkills as string[]) : [];
      setResumeUploadInfo({ id: document.id, targetRole: String(profile.targetRole ?? "").trim() || "미확인", skills });
    } catch (e) {
      setResumeUploadError(e instanceof Error ? e.message : "이력서 파일을 읽지 못했습니다.");
    } finally {
      setResumeUploading(false);
      if (resumeFileInputRef.current) resumeFileInputRef.current.value = "";
    }
  };

  // 자소서 채팅 답변용 참고자료 확보가 목적이라 프로필 반영은 필수 단계가 아니고, 원하면
  // 추가로 눌러서 career-profile(직무/전공/경력/기술요약)에도 반영할 수 있게 한 선택 버튼이다.
  const handleApplyResumeProfile = async () => {
    if (!resumeUploadInfo) return;
    setApplyingResumeProfile(true);
    try {
      await applyResumeExtraction(resumeUploadInfo.id);
    } catch (e) {
      setResumeUploadError(e instanceof Error ? e.message : "프로필 반영에 실패했습니다.");
    } finally {
      setApplyingResumeProfile(false);
    }
  };

  const startGuided = () => { resetEditor(); setEditorMode("guided"); };
  const startFreeform = () => { resetEditor(); setEditorMode("freeform"); };
  const startEdit = (entry: SelfIntroduction) => {
    resetEditor();
    setEditorMode("freeform"); setEditingId(entry.id); setTitle(entry.title);
    setContent(entry.content); setPrimary(entry.primary);
  };

  // 질문 목록(기본 4문항 또는 회사 양식 파싱 결과)이 정해지면 채팅을 시작한다 - 첫 질문을
  // 봇 말풍선으로 띄우고 답변 배열을 그 길이만큼 빈 문자열로 초기화한다.
  const startChatWithQuestions = (list: string[]) => {
    setActiveQuestions(list);
    setAnswers(list.map(() => ""));
    setChatQuestionIndex(0);
    setChatMessages([{ id: nextChatId(), role: "bot", text: list[0] }]);
    setChatPhase("chatting");
  };

  const handleParseCompanyFormat = async () => {
    if (!companyFormatText.trim()) return;
    setParsingCompanyFormat(true);
    setCompanyFormatError(null);
    try {
      const result = await parseCompanyQuestions(companyFormatText.trim());
      if (!result.ok || result.questions.length === 0) {
        setCompanyFormatError(result.message ?? "질문 항목을 찾지 못했습니다.");
        return;
      }
      startChatWithQuestions(result.questions);
    } catch (e) {
      setCompanyFormatError(e instanceof Error ? e.message : "양식 분석 중 오류가 발생했습니다.");
    } finally {
      setParsingCompanyFormat(false);
    }
  };

  // 마지막 질문까지 답하면 자동으로 지금까지의 답변을 모아 초안을 생성하고 chatPhase를
  // "done"으로 넘긴다 - handleGenerate와 로직은 비슷하지만 activeQuestions(회사 양식일 수
  // 있음)를 answers와 같은 순서로 함께 서버에 넘긴다는 점이 다르다.
  const finishChatAndGenerate = async (finalAnswers: string[]) => {
    setBusy(true);
    setErrorMessage(null);
    try {
      const result = await generateSelfIntroductionDraft(job, techSummary, finalAnswers, activeQuestions, resumeContext);
      if (!result.ok || !result.content) {
        setChatMessages((prev) => [
          ...prev,
          { id: nextChatId(), role: "bot", text: result.message ?? "자기소개서 생성에 실패했습니다. 다시 시도해주세요." },
        ]);
        return;
      }
      setContent(result.content);
      setChatMessages((prev) => [
        ...prev,
        { id: nextChatId(), role: "bot", text: "답변 감사합니다! 지금까지 내용을 바탕으로 초안을 작성했어요. 아래에서 자유롭게 다듬어보세요." },
      ]);
      setChatPhase("done");
    } catch (e) {
      setChatMessages((prev) => [
        ...prev,
        { id: nextChatId(), role: "bot", text: e instanceof Error ? `생성 중 오류가 발생했어요: ${e.message}` : "생성 중 오류가 발생했어요." },
      ]);
    } finally {
      setBusy(false);
    }
  };

  const handleSendChatAnswer = () => {
    if (busy) return;
    const answerText = chatInput.trim();
    const nextAnswers = answers.map((a, idx) => (idx === chatQuestionIndex ? answerText : a));
    setAnswers(nextAnswers);
    setChatInput("");
    setChatMessages((prev) => [
      ...prev,
      { id: nextChatId(), role: "user", text: answerText || "(건너뛰기)" },
    ]);

    const nextIndex = chatQuestionIndex + 1;
    if (nextIndex < activeQuestions.length) {
      setChatQuestionIndex(nextIndex);
      setChatMessages((prev) => [...prev, { id: nextChatId(), role: "bot", text: activeQuestions[nextIndex] }]);
    } else {
      void finishChatAndGenerate(nextAnswers);
    }
  };

  const handleChatInputKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSendChatAnswer();
    }
  };

  const handleCritique = async () => {
    setBusy(true); setErrorMessage(null); setCritique(null);
    try {
      const result = await critiqueSelfIntroduction(content, job, techSummary);
      if (!result.ok) { setErrorMessage(result.message ?? "첨삭에 실패했습니다."); return; }
      setCritique(result);
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "첨삭 중 오류가 발생했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleSave = async () => {
    if (!title.trim() || !content.trim()) { setErrorMessage("제목과 내용을 모두 입력해주세요."); return; }
    setBusy(true); setErrorMessage(null);
    try {
      const input = { title: title.trim(), content: content.trim(), primary };
      const saved = editingId ? await updateSelfIntroduction(editingId, input) : await createSelfIntroduction(input);
      setEntries((prev) => {
        const withoutSaved = prev.filter((e) => e.id !== saved.id);
        // 대표로 지정했으면 다른 항목의 primary는 백엔드가 이미 꺼줬으니, 프론트 목록도 같이 갱신.
        const cleared = saved.primary ? withoutSaved.map((e) => ({ ...e, primary: false })) : withoutSaved;
        return [saved, ...cleared];
      });
      resetEditor();
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "저장 중 오류가 발생했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm("이 자기소개서를 삭제할까요?")) return;
    try {
      await deleteSelfIntroduction(id);
      setEntries((prev) => prev.filter((e) => e.id !== id));
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "삭제 중 오류가 발생했습니다.");
    }
  };

  if (loading) return <p>불러오는 중...</p>;

  return (
    <div>
      {errorMessage && <div className="auth-error">{errorMessage}</div>}

      {!editorMode && (
        <>
          <div className="form-actions" style={{ marginBottom: 16 }}>
            <button className="primary-button" onClick={startGuided}>질문식으로 작성</button>
            <button className="outline-button" onClick={startFreeform}>직접 쓰고 첨삭받기</button>
          </div>

          {entries.length === 0 && <p>아직 작성한 자기소개서가 없습니다.</p>}
          {entries.map((entry) => (
            <div key={entry.id} className="panel" style={{ marginBottom: 12, padding: 16 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <strong>{entry.title}{entry.primary && <span style={{ marginLeft: 8, fontSize: 12, color: "#596ff3" }}>대표</span>}</strong>
                <div className="form-actions">
                  <button className="outline-button" onClick={() => startEdit(entry)}>수정</button>
                  <button className="outline-button" onClick={() => handleDelete(entry.id)}>삭제</button>
                </div>
              </div>
              <p style={{ whiteSpace: "pre-wrap", marginTop: 8 }}>{entry.content}</p>
            </div>
          ))}
        </>
      )}

      {editorMode === "guided" && chatPhase === "setup" && (
        <div className="form-section">
          <h3>이력서 파일이 있나요? (선택)</h3>
          <p style={{ color: "#6a7383", fontSize: 13, marginTop: -8 }}>
            PDF/DOCX 이력서를 올리면 내용을 스캔해서 답변을 다듬을 때 참고자료로 써요.
            지원동기 같은 질문은 이력서만으로 채울 수 없어서 그대로 채팅으로 물어봐요.
          </p>
          <input
            ref={resumeFileInputRef}
            type="file"
            accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            disabled={resumeUploading}
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) void handleResumeFileSelected(file);
            }}
          />
          {resumeUploading && <p style={{ fontSize: 12, color: "#9098a7", marginTop: 6 }}>이력서 분석 중...</p>}
          {resumeUploadError && <div className="account-alert error">{resumeUploadError}</div>}
          {resumeUploadInfo && (
            <div className="account-alert" style={{ marginTop: 8 }}>
              <Upload size={14} /> 이력서에서 직무: {resumeUploadInfo.targetRole}
              {resumeUploadInfo.skills.length > 0 && ` · 기술: ${resumeUploadInfo.skills.join(", ")}`}를 찾았어요. 답변을
              다듬을 때 참고할게요.
              <div className="form-actions" style={{ marginTop: 6 }}>
                <button
                  type="button"
                  className="outline-button"
                  disabled={applyingResumeProfile}
                  onClick={() => void handleApplyResumeProfile()}
                >
                  {applyingResumeProfile ? "반영 중..." : "프로필에도 반영하기"}
                </button>
              </div>
            </div>
          )}

          <h3 style={{ marginTop: 20 }}>회사 자소서 양식이 있나요?</h3>
          <p style={{ color: "#6a7383", fontSize: 13, marginTop: -8 }}>
            채용 페이지의 자기소개서 문항을 그대로 붙여넣으면 그 문항 기준으로, 없으면 기본
            질문(지원동기·가치관·강점약점·포부) 기준으로 채팅을 시작해요.
          </p>
          <div className="form-fields">
            <label className="wide">
              회사 자소서 문항 (선택)
              <textarea
                rows={4}
                value={companyFormatText}
                onChange={(e) => setCompanyFormatText(e.target.value)}
                placeholder={"예) 1. 지원동기를 작성하세요(500자 이내)\n2. 성장과정에서 겪은 실패 경험과 극복 방법을 서술하세요"}
              />
            </label>
          </div>
          {companyFormatError && <div className="account-alert error">{companyFormatError}</div>}
          <div className="form-actions">
            <button className="outline-button" onClick={() => startChatWithQuestions(questions)}>
              기본 질문으로 시작하기
            </button>
            <button
              className="primary-button"
              disabled={!companyFormatText.trim() || parsingCompanyFormat}
              onClick={() => void handleParseCompanyFormat()}
            >
              {parsingCompanyFormat ? "양식 분석 중..." : "이 양식으로 질문 만들기"}
            </button>
          </div>
        </div>
      )}

      {editorMode === "guided" && (chatPhase === "chatting" || chatPhase === "done") && (
        <div>
          <div className="interview-chat-log" ref={chatLogRef}>
            {chatMessages.map((m) => (
              <SelfIntroChatBubble key={m.id} message={m} />
            ))}
            {busy && (
              <div className="interview-chat-bubble bot">
                <span className="interview-chat-avatar interview-chat-avatar-cat">
                  <img src="/mascot-code.png" alt="" />
                </span>
                <div className="interview-chat-bubble-body" style={{ color: "#9098a7" }}>
                  답변을 정리해서 초안을 작성하고 있어요...
                </div>
              </div>
            )}
          </div>

          {chatPhase === "chatting" && (
            <div className="interview-chat-input-row">
              <textarea
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                onKeyDown={handleChatInputKeyDown}
                placeholder="답변을 입력해주세요 (비워두고 보내면 건너뛰어요, Enter로 전송)"
                rows={2}
                disabled={busy}
              />
              <button type="button" className="primary-button" onClick={handleSendChatAnswer} disabled={busy}>
                <Send size={14} />
              </button>
            </div>
          )}

          {chatPhase === "done" && content && (
            <div className="form-section">
              <h3>생성된 자기소개서 (자유롭게 수정하세요)</h3>
              <div className="form-fields">
                <label className="wide">
                  <textarea rows={10} value={content} onChange={(e) => setContent(e.target.value)} />
                </label>
              </div>
            </div>
          )}
          {chatPhase === "done" && renderSaveFields()}
        </div>
      )}

      {editorMode === "freeform" && (
        <div>
          <h3>{editingId ? "자기소개서 수정" : "자기소개서 직접 작성"}</h3>
          <div className="form-section">
            <div className="form-fields">
              <label className="wide">
                내용
                <textarea rows={10} value={content} onChange={(e) => setContent(e.target.value)} placeholder="자기소개서 내용을 붙여넣거나 직접 작성해주세요." />
              </label>
            </div>
          </div>
          <div className="form-actions">
            <button className="outline-button" disabled={busy || !content.trim()} onClick={handleCritique}>
              {busy ? "분석 중..." : "AI 첨삭받기"}
            </button>
          </div>

          {critique && (
            <div className="panel" style={{ padding: 16, marginTop: 12 }}>
              {critique.strengths.length > 0 && (
                <div><strong>잘한 점</strong><ul>{critique.strengths.map((s) => <li key={s}>{s}</li>)}</ul></div>
              )}
              {critique.improvements.length > 0 && (
                <div><strong>개선하면 좋을 점</strong><ul>{critique.improvements.map((s) => <li key={s}>{s}</li>)}</ul></div>
              )}
              {critique.revised_example && (
                <div><strong>수정 예시</strong><p style={{ whiteSpace: "pre-wrap" }}>{critique.revised_example}</p></div>
              )}
            </div>
          )}
          {renderSaveFields()}
        </div>
      )}
    </div>
  );

  function renderSaveFields() {
    if (!content.trim()) return null;
    return (
      <div className="form-section">
        <div className="form-fields">
          <label>제목<input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="예: 백엔드 개발자 지원용" /></label>
          <label style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <input type="checkbox" checked={primary} onChange={(e) => setPrimary(e.target.checked)} />
            대표 자기소개서로 지정
          </label>
        </div>
        <div className="form-actions">
          <button className="outline-button" onClick={resetEditor}>취소</button>
          <button className="primary-button" disabled={busy} onClick={handleSave}>{busy ? "저장 중..." : "저장"}</button>
        </div>
      </div>
    );
  }
}

// 사이트 챗봇(SiteAssistantWidget)의 ChatBubble과 같은 구조 - 봇 아바타만 아이콘 대신
// 마스코트 이미지(mascot-code.png, 노트북+코드 말풍선 포즈)를 쓴다.
function SelfIntroChatBubble({ message }: { message: { role: "bot" | "user"; text: string } }) {
  if (message.role === "user") {
    return (
      <div className="interview-chat-bubble user">
        <span className="interview-chat-avatar">나</span>
        <div className="interview-chat-bubble-body">{message.text}</div>
      </div>
    );
  }
  return (
    <div className="interview-chat-bubble bot">
      <span className="interview-chat-avatar interview-chat-avatar-cat">
        <img src="/mascot-code.png" alt="" />
      </span>
      <div className="interview-chat-bubble-body">{message.text}</div>
    </div>
  );
}

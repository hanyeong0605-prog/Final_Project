import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { Bot, LoaderCircle, MessageCircle, Send, User, X } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { sendAssistantMessage } from "../api/assistantApi";
import type { AssistantChatTurn, AssistantJobReference } from "../api/assistantApi";
import { useSiteAssistantWidget } from "../model/SiteAssistantWidgetContext";

// 2026-08-10: mock-interview/components/InterviewChatWidget.tsx를 대체하는 사이트 전체
// 범용 도우미 위젯. 예전엔 이 아이콘을 누르면 "모의면접 연습 채팅"이 바로 떴는데, 이제
// 모의면접 연습은 /mock-interview 페이지에서만 시작하고(이미 그 페이지에 전용 채팅
// 모드가 따로 있음, 2026-08-06 변경), 이 위젯은 사이트 어디서든 질문하고 필요하면 관련
// 페이지로 이동까지 도와주는 범용 챗봇이 됐다(assistant/chat.py의 navigate_to).
//
// CSS 클래스는 InterviewChatWidget이 쓰던 .interview-chat-* 이름을 그대로 재사용한다 -
// 이름은 "interview"지만 실제로는 범용 채팅 위젯 스타일(말풍선/패널/입력창)이라 내용상
// 문제 없고, 클래스 이름을 전부 바꾸는 건 이 작업 범위를 벗어나는 순수 리네이밍이라
// 남겨뒀다.
type ChatMessage = {
  id: string;
  role: "bot" | "user";
  kind: "text" | "error";
  text: string;
  jobReferences?: AssistantJobReference[];
};

let messageIdCounter = 0;
function nextId(): string {
  messageIdCounter += 1;
  return `assistant-msg-${messageIdCounter}`;
}

const GREETING: ChatMessage = {
  id: nextId(),
  role: "bot",
  kind: "text",
  text: "안녕하세요! Job-A-Dream AI 도우미예요. 채용공고 찾는 법, 이력서 작성, 면접 준비 등 뭐든 물어보세요. 필요하면 관련 페이지로 바로 안내해드릴게요.",
};

// 서버로 보내는 대화 기록도 너무 길게 쌓이지 않게 최근 몇 턴만 유지한다(assistant/chat.py의
// _MAX_HISTORY_TURNS와 같은 이유 - 최신 맥락이 더 중요하고 토큰도 아낀다).
const MAX_HISTORY_TURNS = 10;

function isNavigationApproval(value: string): boolean {
  return /^(네|넵|예|응|어|좋아|좋아요|그래|그럼|이동해줘|가줘|yes|y)$/i.test(value.trim());
}

function isNavigationDecline(value: string): boolean {
  return /^(아니|아니요|아뇨|괜찮아|괜찮아요|싫어|no|n)$/i.test(value.trim());
}

function safeJobUrl(sourceUrl: string): string | null {
  try {
    const url = new URL(sourceUrl);
    return url.protocol === "https:" || url.protocol === "http:" ? url.href : null;
  } catch {
    return null;
  }
}

export function SiteAssistantWidget() {
  const { open, closeChat, toggleChat } = useSiteAssistantWidget();
  const navigate = useNavigate();
  const [messages, setMessages] = useState<ChatMessage[]>([GREETING]);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [pendingNavigation, setPendingNavigation] = useState<string | null>(null);
  const logRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, sending, open]);

  const send = async () => {
    const text = draft.trim();
    if (!text || sending) return;

    if (pendingNavigation && isNavigationApproval(text)) {
      setMessages((prev) => [...prev,
        { id: nextId(), role: "user", kind: "text", text },
        { id: nextId(), role: "bot", kind: "text", text: "네, 요청하신 페이지로 이동할게요." },
      ]);
      setDraft("");
      const destination = pendingNavigation;
      setPendingNavigation(null);
      navigate(destination);
      return;
    }

    if (pendingNavigation && isNavigationDecline(text)) {
      setMessages((prev) => [...prev,
        { id: nextId(), role: "user", kind: "text", text },
        { id: nextId(), role: "bot", kind: "text", text: "알겠습니다. 현재 페이지에 머무를게요. 다른 궁금한 점을 물어보세요." },
      ]);
      setDraft("");
      setPendingNavigation(null);
      return;
    }

    const history: AssistantChatTurn[] = messages
      .filter((m) => m.kind === "text")
      .slice(-MAX_HISTORY_TURNS)
      .map((m) => ({ role: m.role === "user" ? "user" : "assistant", content: m.text }));

    setMessages((prev) => [...prev, { id: nextId(), role: "user", kind: "text", text }]);
    setDraft("");
    setSending(true);
    try {
      const result = await sendAssistantMessage(text, history);
      if (!result.ok) {
        setMessages((prev) => [
          ...prev,
          { id: nextId(), role: "bot", kind: "error", text: result.message ?? "답변을 받지 못했어요." },
        ]);
        return;
      }
      setMessages((prev) => [...prev, {
        id: nextId(), role: "bot", kind: "text", text: result.reply ?? "", jobReferences: result.job_references ?? [],
      }]);
      setPendingNavigation(result.suggested_navigate_to ?? null);
    } catch (error) {
      setMessages((prev) => [
        ...prev,
        {
          id: nextId(),
          role: "bot",
          kind: "error",
          text: error instanceof Error ? `답변을 받지 못했어요: ${error.message}` : "답변을 받지 못했어요. 잠시 후 다시 시도해 주세요.",
        },
      ]);
    } finally {
      setSending(false);
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void send();
    }
  };

  return (
    <>
      {open && (
        <div className="interview-chat-widget-panel">
          <div className="interview-chat-widget-header">
            <span>
              <Bot size={16} /> Job-A-Dream AI 도우미
            </span>
            <button type="button" onClick={closeChat} aria-label="챗봇 닫기">
              <X size={16} />
            </button>
          </div>

          <div className="interview-chat-log interview-chat-widget-log" ref={logRef}>
            {messages.map((m) => (
              <ChatBubble key={m.id} message={m} />
            ))}
            {sending && (
              <div className="interview-chat-bubble bot">
                <span className="interview-chat-avatar">
                  <Bot size={16} />
                </span>
                <div className="interview-chat-bubble-body" style={{ display: "flex", alignItems: "center", gap: 6, color: "#9098a7" }}>
                  <LoaderCircle className="spin" size={14} /> 생각하고 있어요...
                </div>
              </div>
            )}
          </div>

          <div className="interview-chat-input-row" style={{ padding: "14px" }}>
            <textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="궁금한 걸 물어보세요... (Enter로 전송)"
              rows={2}
              disabled={sending}
            />
            <button type="button" className="primary-button" onClick={() => void send()} disabled={!draft.trim() || sending}>
              <Send size={14} />
            </button>
          </div>
        </div>
      )}

      <button
        type="button"
        className={`interview-chat-widget-toggle${open ? " is-open" : ""}`}
        onClick={toggleChat}
        aria-label={open ? "도우미 챗봇 닫기" : "도우미 챗봇 열기"}
      >
        {open ? (
          <X size={22} />
        ) : (
          <>
            <span className="interview-chat-widget-icon" aria-hidden="true"><MessageCircle size={22} /></span>
            <span className="interview-chat-widget-mascot" aria-hidden="true" />
            <span className="interview-chat-widget-greeting" aria-hidden="true">무엇이든<br />물어보세요!</span>
          </>
        )}
      </button>
    </>
  );
}

function ChatBubble({ message }: { message: ChatMessage }) {
  if (message.role === "user") {
    return (
      <div className="interview-chat-bubble user">
        <span className="interview-chat-avatar">
          <User size={16} />
        </span>
        <div className="interview-chat-bubble-body">{message.text}</div>
      </div>
    );
  }

  const jobReferences = message.jobReferences
    ?.map((job) => ({ job, url: safeJobUrl(job.source_url) }))
    .filter((item): item is { job: AssistantJobReference; url: string } => item.url !== null);

  return (
    <div className={`interview-chat-bubble bot${message.kind === "error" ? " error" : ""}`}>
      <span className="interview-chat-avatar">
        <Bot size={16} />
      </span>
      <div className="interview-chat-bubble-body">
        {message.text}
        {!!jobReferences?.length && (
          <div className="assistant-job-reference-list">
            {jobReferences.map(({ job, url }) => (
              <a key={job.job_posting_id} href={url} target="_blank" rel="noreferrer" className="assistant-job-reference">
                <strong>{job.company_name ? `${job.company_name} · ` : ""}{job.title}</strong>
                <span>적합도 {Math.round(job.readiness_score)}점{job.location ? ` · ${job.location}` : ""}</span>
              </a>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

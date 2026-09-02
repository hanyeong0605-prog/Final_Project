import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { ArrowRight, Bot, LoaderCircle, MessageCircle, Send, User, X } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { sendAssistantMessage } from "../api/assistantApi";
import type { AssistantChatTurn, AssistantJobReference, AssistantSuggestedPage } from "../api/assistantApi";
import { readNavigationIntent } from "../lib/navigationConsent";
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
  // 이 답변에 딸린 "이 페이지로 갈까요?" 제안. 있으면 말풍선 아래에 미리보기 카드가 붙는다.
  suggestedPage?: AssistantSuggestedPage;
};

// 마지막으로 받은 이동 제안. 사용자가 말로 "네"라고 답했을 때 어디로 보낼지 기억해둔다.
type PendingNavigation = { messageId: string; page: AssistantSuggestedPage };

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

// "마이페이지 페이지로"처럼 말이 겹치지 않게 이름 뒤에 조사만 붙인다. 받침이 없거나 ㄹ이면
// "로", 그 외에는 "으로"다("홈으로", "마이페이지로", "AI 모의면접으로").
function withDirectionParticle(name: string): string {
  const last = name.trim().slice(-1);
  const code = last.charCodeAt(0);
  if (Number.isNaN(code) || code < 0xac00 || code > 0xd7a3) return `${name}(으)로`;
  const finalConsonant = (code - 0xac00) % 28;
  return `${name}${finalConsonant === 0 || finalConsonant === 8 ? "로" : "으로"}`;
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
  const [pendingNavigation, setPendingNavigation] = useState<PendingNavigation | null>(null);
  // 미리보기 카드에서 이미 이동/거절을 고른 답변. 대화 기록에 남은 카드의 버튼을 다시
  // 누르는 걸 막고, 어떻게 처리됐는지 카드에 표시하기 위한 것.
  const [resolvedCards, setResolvedCards] = useState<Record<string, "accepted" | "declined">>({});
  const logRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, sending, open]);

  const approveNavigation = (pending: PendingNavigation, userText?: string) => {
    setMessages((prev) => [...prev,
      ...(userText ? [{ id: nextId(), role: "user" as const, kind: "text" as const, text: userText }] : []),
      { id: nextId(), role: "bot", kind: "text", text: `네, ${withDirectionParticle(pending.page.name)} 이동할게요.` },
    ]);
    setResolvedCards((prev) => ({ ...prev, [pending.messageId]: "accepted" }));
    setPendingNavigation(null);
    navigate(pending.page.path);
  };

  const declineNavigation = (pending: PendingNavigation, userText?: string) => {
    setMessages((prev) => [...prev,
      ...(userText ? [{ id: nextId(), role: "user" as const, kind: "text" as const, text: userText }] : []),
      { id: nextId(), role: "bot", kind: "text", text: "알겠습니다. 현재 페이지에 머무를게요. 다른 궁금한 점을 물어보세요." },
    ]);
    setResolvedCards((prev) => ({ ...prev, [pending.messageId]: "declined" }));
    setPendingNavigation(null);
  };

  const send = async () => {
    const text = draft.trim();
    if (!text || sending) return;

    if (pendingNavigation) {
      const intent = readNavigationIntent(text);
      if (intent === "approve") {
        setDraft("");
        approveNavigation(pendingNavigation, text);
        return;
      }
      if (intent === "decline") {
        setDraft("");
        declineNavigation(pendingNavigation, text);
        return;
      }
      // "unrelated"면 새 질문이므로 아래 일반 경로로 흘려보낸다.
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
      // 경로와 미리보기 정보가 둘 다 있을 때만 제안으로 인정한다 - 서버가 목록에 없는
      // 경로를 걸러내면 둘 다 null로 내려오므로, 여기서도 자연히 제안이 사라진다.
      const suggestedPage = result.suggested_navigate_to && result.suggested_page ? result.suggested_page : null;
      const replyId = nextId();
      setMessages((prev) => [...prev, {
        id: replyId,
        role: "bot",
        kind: "text",
        text: result.reply ?? "",
        jobReferences: result.job_references ?? [],
        ...(suggestedPage ? { suggestedPage } : {}),
      }]);
      setPendingNavigation(suggestedPage ? { messageId: replyId, page: suggestedPage } : null);
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
              <ChatBubble
                key={m.id}
                message={m}
                cardState={resolvedCards[m.id]}
                onApprove={() => m.suggestedPage && approveNavigation({ messageId: m.id, page: m.suggestedPage })}
                onDecline={() => m.suggestedPage && declineNavigation({ messageId: m.id, page: m.suggestedPage })}
              />
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

function ChatBubble({ message, cardState, onApprove, onDecline }: {
  message: ChatMessage;
  cardState?: "accepted" | "declined";
  onApprove: () => void;
  onDecline: () => void;
}) {
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
        {message.suggestedPage && (
          <NavigationPreviewCard
            page={message.suggestedPage}
            state={cardState}
            onApprove={onApprove}
            onDecline={onDecline}
          />
        )}
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

// 이동 전 미리보기 카드. 예전엔 "이동할까요?"라는 문장 하나만 나와서 사용자는 그 페이지에
// 뭐가 있는지 모른 채 답해야 했고, 수락도 오직 타이핑으로만 가능했다(그마저도 표현이 조금만
// 달라지면 못 알아들었다). 이제 갈 곳의 내용을 먼저 보여주고, 말로 답하든 버튼을 누르든
// 똑같이 이동할 수 있다. 어느 쪽이든 이동은 사용자가 고른 뒤에만 일어난다.
function NavigationPreviewCard({ page, state, onApprove, onDecline }: {
  page: AssistantSuggestedPage;
  state?: "accepted" | "declined";
  onApprove: () => void;
  onDecline: () => void;
}) {
  return (
    <div className="assistant-nav-preview">
      <strong>{page.name}</strong>
      <span className="assistant-nav-preview-desc">{page.description}</span>
      {!!page.highlights.length && (
        <ul>
          {page.highlights.map((highlight) => (
            <li key={highlight}>{highlight}</li>
          ))}
        </ul>
      )}
      {state ? (
        <span className="assistant-nav-preview-done">
          {state === "accepted" ? "이 페이지로 이동했어요." : "이동하지 않았어요."}
        </span>
      ) : (
        <>
          <strong className="assistant-nav-preview-ask">
            자세히 보려면 {withDirectionParticle(page.name)} 이동해야 해요. 이동할까요?
          </strong>
          <div className="assistant-nav-preview-actions">
            <button type="button" className="assistant-nav-preview-go" onClick={onApprove}>
              예 <ArrowRight size={13} />
            </button>
            <button type="button" className="assistant-nav-preview-stay" onClick={onDecline}>
              아니오
            </button>
          </div>
        </>
      )}
    </div>
  );
}

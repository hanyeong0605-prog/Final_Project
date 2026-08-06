import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { Bot, CheckCircle2, Lightbulb, LoaderCircle, MessageCircle, Quote, Send, User, X } from "lucide-react";
import { evaluateAnswer, fetchNextQuestion } from "../api/mockInterviewApi";
import type { EvaluationReport } from "../model/mockInterview.types";
import { useInterviewChatWidget } from "../model/InterviewChatWidgetContext";

// 2026-08-06: 페이지 안에 박혀있던 "카메라·마이크 없이 채팅으로 연습하기" 링크 대신, 홈페이지
// 어디서든 오른쪽 아래 동그란 아이콘을 눌러 바로 열 수 있는 위젯으로 바꿔달라는 요청으로
// InterviewChatPage.tsx의 로직을 그대로 가져와 재사용 가능한 위젯으로 옮겼다. AppShell에
// 마운트해서 로그인한 모든 페이지에서 항상 떠 있게 한다(사이트 전역 플로팅 챗봇).
//
// ai-server question_generator.py의 QUESTION_CATEGORIES와 값(value)이 반드시 같아야 학습된
// 카테고리 프롬프트 패턴과 맞는다 - 한쪽만 바뀌면 카테고리 지정 효과가 사라지니 같이 고쳐야 한다.
const CATEGORY_OPTIONS: { value: string; label: string }[] = [
  { value: "자기소개_지원동기", label: "자기소개·지원동기" },
  { value: "가치관_자기관리", label: "가치관·자기관리" },
  { value: "협업_리더십_커뮤니케이션", label: "협업·리더십·커뮤니케이션" },
  { value: "기술_직무역량", label: "기술·직무역량" },
  { value: "문제해결_도전경험", label: "문제해결·도전경험" },
  { value: "강점_약점", label: "강점·약점" },
];

type ChatMessage =
  | { id: string; role: "bot"; kind: "text"; text: string }
  | { id: string; role: "bot"; kind: "feedback"; report: EvaluationReport }
  | { id: string; role: "bot"; kind: "error"; text: string }
  | { id: string; role: "user"; kind: "text"; text: string };

let messageIdCounter = 0;
function nextId(): string {
  messageIdCounter += 1;
  return `chat-msg-${messageIdCounter}`;
}

const GREETING: ChatMessage = {
  id: nextId(),
  role: "bot",
  kind: "text",
  text: "안녕하세요! 카메라·마이크 없이도 면접 연습할 수 있어요. 아래에서 카테고리를 골라주시면 질문 드릴게요.",
};

export function InterviewChatWidget() {
  const { open, closeChat, toggleChat } = useInterviewChatWidget();
  const [messages, setMessages] = useState<ChatMessage[]>([GREETING]);
  // 지금 답변을 기다리고 있는 질문 - null이면 카테고리부터 골라야 하는 상태(입력창 비활성화).
  const [currentQuestion, setCurrentQuestion] = useState<string | null>(null);
  const [answerDraft, setAnswerDraft] = useState("");
  const [loadingQuestion, setLoadingQuestion] = useState(false);
  const [loadingFeedback, setLoadingFeedback] = useState(false);
  const logRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, loadingQuestion, loadingFeedback, open]);

  const busy = loadingQuestion || loadingFeedback;

  const requestQuestion = async (categoryValue: string, categoryLabel: string) => {
    setCurrentQuestion(null);
    setLoadingQuestion(true);
    try {
      const res = await fetchNextQuestion(undefined, undefined, categoryValue);
      setMessages((prev) => [...prev, { id: nextId(), role: "bot", kind: "text", text: res.question }]);
      setCurrentQuestion(res.question);
    } catch (error) {
      setMessages((prev) => [
        ...prev,
        {
          id: nextId(),
          role: "bot",
          kind: "error",
          text:
            error instanceof Error
              ? `"${categoryLabel}" 질문을 준비하지 못했어요: ${error.message}`
              : `"${categoryLabel}" 질문을 준비하지 못했어요. 잠시 후 다시 시도해 주세요.`,
        },
      ]);
    } finally {
      setLoadingQuestion(false);
    }
  };

  const submitAnswer = async () => {
    const transcript = answerDraft.trim();
    if (!transcript || !currentQuestion || busy) return;

    const question = currentQuestion;
    setMessages((prev) => [...prev, { id: nextId(), role: "user", kind: "text", text: transcript }]);
    setAnswerDraft("");
    setCurrentQuestion(null); // 피드백 받기 전까지, 그리고 다음 카테고리 고르기 전까지 재전송 막기
    setLoadingFeedback(true);
    try {
      // 음성/얼굴 지표 없이 텍스트만 있는 답변이라 voiceMetrics/faceMetrics는 null로 보낸다 -
      // evaluateAnswer/evaluation.py 둘 다 선택값으로 받게 설계돼 있어서 그대로 동작한다.
      const res = await evaluateAnswer(question, transcript, null, null);
      setMessages((prev) => [...prev, { id: nextId(), role: "bot", kind: "feedback", report: res.report }]);
    } catch (error) {
      setMessages((prev) => [
        ...prev,
        {
          id: nextId(),
          role: "bot",
          kind: "error",
          text:
            error instanceof Error
              ? `피드백을 받지 못했어요: ${error.message}`
              : "피드백을 받지 못했어요. 잠시 후 다시 시도해 주세요.",
        },
      ]);
    } finally {
      setLoadingFeedback(false);
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void submitAnswer();
    }
  };

  return (
    <>
      {open && (
        <div className="interview-chat-widget-panel">
          <div className="interview-chat-widget-header">
            <span>
              <Bot size={16} /> 면접 연습 챗봇
            </span>
            <button type="button" onClick={closeChat} aria-label="챗봇 닫기">
              <X size={16} />
            </button>
          </div>

          <div className="interview-chat-log interview-chat-widget-log" ref={logRef}>
            {messages.map((m) => (
              <ChatBubble key={m.id} message={m} />
            ))}
            {loadingQuestion && <TypingBubble text="질문을 준비하고 있어요..." />}
            {loadingFeedback && <TypingBubble text="답변을 분석하고 있어요..." />}
          </div>

          <div className="interview-chat-category-row" style={{ padding: "0 14px" }}>
            {CATEGORY_OPTIONS.map((c) => (
              <button
                key={c.value}
                type="button"
                className="interview-chat-category-btn"
                disabled={busy}
                onClick={() => void requestQuestion(c.value, c.label)}
              >
                {c.label}
              </button>
            ))}
          </div>

          <div className="interview-chat-input-row" style={{ padding: "14px", marginTop: 10 }}>
            <textarea
              value={answerDraft}
              onChange={(e) => setAnswerDraft(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={currentQuestion ? "답변 입력... (Enter로 전송)" : "카테고리를 먼저 골라주세요"}
              rows={2}
              disabled={!currentQuestion || busy}
            />
            <button
              type="button"
              className="primary-button"
              onClick={() => void submitAnswer()}
              disabled={!currentQuestion || !answerDraft.trim() || busy}
            >
              <Send size={14} />
            </button>
          </div>
        </div>
      )}

      <button
        type="button"
        className="interview-chat-widget-toggle"
        onClick={toggleChat}
        aria-label={open ? "면접 연습 챗봇 닫기" : "면접 연습 챗봇 열기"}
      >
        {open ? <X size={22} /> : <MessageCircle size={22} />}
      </button>
    </>
  );
}

function TypingBubble({ text }: { text: string }) {
  return (
    <div className="interview-chat-bubble bot">
      <span className="interview-chat-avatar">
        <Bot size={16} />
      </span>
      <div className="interview-chat-bubble-body" style={{ display: "flex", alignItems: "center", gap: 6, color: "#9098a7" }}>
        <LoaderCircle className="spin" size={14} /> {text}
      </div>
    </div>
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

  if (message.kind === "feedback") {
    return (
      <div className="interview-chat-bubble bot" style={{ maxWidth: "96%" }}>
        <span className="interview-chat-avatar">
          <Bot size={16} />
        </span>
        <div className="interview-chat-bubble-body">
          <FeedbackContent report={message.report} />
        </div>
      </div>
    );
  }

  return (
    <div className={`interview-chat-bubble bot${message.kind === "error" ? " error" : ""}`}>
      <span className="interview-chat-avatar">
        <Bot size={16} />
      </span>
      <div className="interview-chat-bubble-body">{message.text}</div>
    </div>
  );
}

function FeedbackContent({ report }: { report: EvaluationReport }) {
  if (!report.ok) {
    return <p style={{ margin: 0 }}>{report.message ?? "평가를 생성하지 못했어요."}</p>;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
      {(report.overall_score !== null || report.content_score !== null || report.delivery_score !== null) && (
        <div className="interview-chat-feedback-scores">
          {report.overall_score !== null && <span>총평 {report.overall_score}점</span>}
          {report.content_score !== null && <span>내용 {report.content_score}점</span>}
          {report.delivery_score !== null && <span>전달력 {report.delivery_score}점</span>}
        </div>
      )}

      {report.strengths.length > 0 && (
        <div>
          <strong style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#2e9e5b" }}>
            <CheckCircle2 size={13} /> 강점
          </strong>
          <ul style={{ margin: "4px 0 0", paddingLeft: 18 }}>
            {report.strengths.map((s, i) => (
              <li key={i} style={{ fontSize: 13 }}>
                {s}
              </li>
            ))}
          </ul>
        </div>
      )}

      {report.improvements.length > 0 && (
        <div>
          <strong style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#c98a1f" }}>
            <Lightbulb size={13} /> 개선점
          </strong>
          <ul style={{ margin: "4px 0 0", paddingLeft: 18 }}>
            {report.improvements.map((s, i) => (
              <li key={i} style={{ fontSize: 13 }}>
                {s}
              </li>
            ))}
          </ul>
        </div>
      )}

      {report.model_answer && (
        <div className="interview-model-answer" style={{ margin: 0 }}>
          <h4>
            <Quote size={13} /> 모범답안
          </h4>
          <p>{report.model_answer}</p>
        </div>
      )}

      <span style={{ fontSize: 11, color: "#9098a7" }}>이어서 연습하고 싶으면 아래에서 카테고리를 다시 골라주세요.</span>
    </div>
  );
}

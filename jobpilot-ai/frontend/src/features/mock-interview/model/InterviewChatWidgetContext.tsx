import { createContext, useContext, useState } from "react";
import type { ReactNode } from "react";

// 2026-08-06: 전역 플로팅 챗봇(InterviewChatWidget)의 열림/닫힘 상태를 다른 페이지(예:
// MockInterviewPage 시작화면의 "채팅으로 연습하기" 카드)에서도 제어할 수 있게 만든 Context.
// 위젯 자체는 AppShell에 한 번만 마운트되지만, 열기 버튼은 여러 페이지에 흩어져 있을 수 있어서
// props로 내려주는 대신 Context로 공유한다.
interface InterviewChatWidgetContextValue {
  open: boolean;
  openChat: () => void;
  closeChat: () => void;
  toggleChat: () => void;
}

const InterviewChatWidgetContext = createContext<InterviewChatWidgetContextValue | null>(null);

export function InterviewChatWidgetProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);

  const value: InterviewChatWidgetContextValue = {
    open,
    openChat: () => setOpen(true),
    closeChat: () => setOpen(false),
    toggleChat: () => setOpen((v) => !v),
  };

  return <InterviewChatWidgetContext.Provider value={value}>{children}</InterviewChatWidgetContext.Provider>;
}

export function useInterviewChatWidget(): InterviewChatWidgetContextValue {
  const ctx = useContext(InterviewChatWidgetContext);
  if (!ctx) {
    throw new Error("useInterviewChatWidget은 InterviewChatWidgetProvider 안에서만 쓸 수 있습니다.");
  }
  return ctx;
}

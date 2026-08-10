import { createContext, useContext, useState } from "react";
import type { ReactNode } from "react";

// 2026-08-10: mock-interview/model/InterviewChatWidgetContext.tsx를 대체한다 - 전역 플로팅
// 위젯이 "모의면접 연습 전용"에서 "사이트 전체 범용 도우미"로 바뀌면서 위치도 함께 옮겼다
// (더 이상 모의면접 도메인 소속이 아니다). 열림/닫힘 상태만 관리하는 건 이전과 동일 -
// 위젯은 AppShell에 한 번만 마운트하고, 이 Context로 다른 곳에서도 열고 닫을 수 있게 한다.
interface SiteAssistantWidgetContextValue {
  open: boolean;
  openChat: () => void;
  closeChat: () => void;
  toggleChat: () => void;
}

const SiteAssistantWidgetContext = createContext<SiteAssistantWidgetContextValue | null>(null);

export function SiteAssistantWidgetProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);

  const value: SiteAssistantWidgetContextValue = {
    open,
    openChat: () => setOpen(true),
    closeChat: () => setOpen(false),
    toggleChat: () => setOpen((v) => !v),
  };

  return <SiteAssistantWidgetContext.Provider value={value}>{children}</SiteAssistantWidgetContext.Provider>;
}

export function useSiteAssistantWidget(): SiteAssistantWidgetContextValue {
  const ctx = useContext(SiteAssistantWidgetContext);
  if (!ctx) {
    throw new Error("useSiteAssistantWidget은 SiteAssistantWidgetProvider 안에서만 쓸 수 있습니다.");
  }
  return ctx;
}

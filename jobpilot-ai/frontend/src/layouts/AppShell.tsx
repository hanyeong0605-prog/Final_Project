import { useState, useEffect } from "react"; /* useEffect 추가 */
import { Bell, ChevronRight, CircleHelp, Github, LogOut, Menu, Plus, X } from "lucide-react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../features/auth/model/AuthContext";
import { getSubscriptionStatus } from "../features/subscription/api/subscriptionApi";
import { navigationItems } from "../shared/constants/navigation";
import { OnboardingModal } from "../features/profile/components/OnboardingModal";
import { SiteAssistantWidget } from "../features/assistant/components/SiteAssistantWidget";
import { SiteAssistantWidgetProvider } from "../features/assistant/model/SiteAssistantWidgetContext";

export function AppShell() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [, setForceRender] = useState(0); //  강제 렌더링용 상태 추가
  const location = useLocation();
  const activeItem = navigationItems.find((item) => item.path === location.pathname) ?? navigationItems[0];
  const { member, logout } = useAuth();
  const [subscribed, setSubscribed] = useState(false);

  // 2026-08-10: 사이드바 프로필 카드에 "구독중" 뱃지 표시용 - AppShell이 모든 페이지를
  // 감싸는 공통 레이아웃이라 여기서 한 번만 조회하면 어느 페이지에서든 뜬다. 실패하면
  // 그냥 뱃지를 안 보여준다(fail-closed - 구독 여부를 확신 못 하면 안 보여주는 게 맞다).
  useEffect(() => {
    void getSubscriptionStatus()
      .then((s) => setSubscribed(s.subscribed))
      .catch(() => setSubscribed(false));
  }, []);

// 카카오 스크립트 로딩으로 인한 렌더링 씹힘 방지용 마운트 훅
  useEffect(() => {
    const timer = setTimeout(() => {
      setForceRender((prev) => prev + 1);
    }, 50); // 0.05초 뒤에 강제로 한 번 더 렌더링
    return () => clearTimeout(timer);
  }, []);

  // 2026-08-10: 이력서 작성 도우미(/resume) 페이지는 그 자체로 AI 작성/첨삭 UI가 이미
  // 있어서, 플로팅 챗봇 아이콘이 화면 구석에 겹쳐 보이면 혼란만 준다는 피드백으로 그
  // 페이지에서만 숨긴다 - 다른 페이지는 기존처럼 전역으로 계속 뜬다(이 위젯이 모의면접
  // 전용에서 사이트 전체 범용 도우미로 바뀐 뒤에도 이 결정은 그대로 유지).
  const hideSiteAssistant = location.pathname.startsWith("/resume");

  // SiteAssistantWidgetProvider로 감싸서, Outlet 안에서 렌더되는 어떤 페이지에서도 이
  // 위젯의 열림 상태를 제어할 수 있게 한다(예전 InterviewChatWidgetProvider와 같은 역할).
  return <SiteAssistantWidgetProvider><div className="app-shell"><OnboardingModal />{!hideSiteAssistant && <SiteAssistantWidget />}
    <aside className={`sidebar ${menuOpen ? "open" : ""}`}>
      <div className="brand"><span className="brand-mark"><span>J</span></span><div><strong>Job-A-Dream AI</strong><small>career action coach</small></div><button className="mobile-close" onClick={() => setMenuOpen(false)} aria-label="메뉴 닫기"><X size={19} /></button></div>
      <div className="profile-card"><div className="avatar">{member?.nickname.slice(0, 1)}</div><div><strong>{member?.nickname}{subscribed && <span className="subscription-badge active sidebar-subscription-badge">구독중</span>}</strong><span>{member?.email}</span></div><button aria-label="로그아웃" title="로그아웃" onClick={logout}><LogOut size={17} /></button></div>
      <nav><span className="nav-label">WORKSPACE</span>{navigationItems.map((item) => { const Icon = item.icon; return <NavLink key={item.path} to={item.path} end={item.path === "/"} className={({ isActive }) => isActive ? "nav-item active" : "nav-item"} onClick={() => setMenuOpen(false)}><Icon size={19} /><span>{item.label}</span></NavLink>; })}</nav>
      <div className="sidebar-bottom"><button className="github-connect"><Github size={18} /><span>GitHub 연결</span><span className="connected-dot" /></button><div className="source-note"><CircleHelp size={15} /><span>추천 결과는 지원 준비도를 안내합니다.<br />최종 지원 전 사람인 원문을 확인하세요.</span></div></div>
    </aside>
    <main className="main-area"><header className="topbar"><button className="menu-button" onClick={() => setMenuOpen(true)} aria-label="메뉴 열기"><Menu size={21} /></button><div className="breadcrumb"><span>Job-A-Dream AI</span><ChevronRight size={15} /><strong>{activeItem.label}</strong></div><div className="top-actions"><button className="add-job"><Plus size={17} />공고 직접 등록</button><button className="bell" aria-label="알림"><Bell size={19} /><span /></button></div></header><section className="content"><Outlet /></section></main>
  </div></SiteAssistantWidgetProvider>;
}

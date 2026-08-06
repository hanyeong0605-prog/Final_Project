import { useState, useEffect } from "react"; /* useEffect 추가 */
import { Bell, ChevronRight, CircleHelp, Github, LogOut, Menu, Plus, X } from "lucide-react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../features/auth/model/AuthContext";
import { navigationItems } from "../shared/constants/navigation";
import { OnboardingModal } from "../features/profile/components/OnboardingModal";
import { InterviewChatWidget } from "../features/mock-interview/components/InterviewChatWidget";
import { InterviewChatWidgetProvider } from "../features/mock-interview/model/InterviewChatWidgetContext";

export function AppShell() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [, setForceRender] = useState(0); //  강제 렌더링용 상태 추가
  const location = useLocation();
  const activeItem = navigationItems.find((item) => item.path === location.pathname) ?? navigationItems[0];
  const { member, logout } = useAuth();

// 카카오 스크립트 로딩으로 인한 렌더링 씹힘 방지용 마운트 훅
  useEffect(() => {
    const timer = setTimeout(() => {
      setForceRender((prev) => prev + 1);
    }, 50); // 0.05초 뒤에 강제로 한 번 더 렌더링
    return () => clearTimeout(timer);
  }, []);

  // InterviewChatWidgetProvider로 감싸서, Outlet 안에서 렌더되는 어떤 페이지(예:
  // MockInterviewPage의 "채팅으로 연습하기" 카드)에서도 이 위젯의 열림 상태를 제어할 수 있게 한다.
  return <InterviewChatWidgetProvider><div className="app-shell"><OnboardingModal /><InterviewChatWidget />
    <aside className={`sidebar ${menuOpen ? "open" : ""}`}>
      <div className="brand"><span className="brand-mark"><span>J</span></span><div><strong>JobPilot AI</strong><small>career action coach</small></div><button className="mobile-close" onClick={() => setMenuOpen(false)} aria-label="메뉴 닫기"><X size={19} /></button></div>
      <div className="profile-card"><div className="avatar">{member?.nickname.slice(0, 1)}</div><div><strong>{member?.nickname}</strong><span>{member?.email}</span></div><button aria-label="로그아웃" title="로그아웃" onClick={logout}><LogOut size={17} /></button></div>
      <nav><span className="nav-label">WORKSPACE</span>{navigationItems.map((item) => { const Icon = item.icon; return <NavLink key={item.path} to={item.path} end={item.path === "/"} className={({ isActive }) => isActive ? "nav-item active" : "nav-item"} onClick={() => setMenuOpen(false)}><Icon size={19} /><span>{item.label}</span></NavLink>; })}</nav>
      <div className="sidebar-bottom"><button className="github-connect"><Github size={18} /><span>GitHub 연결</span><span className="connected-dot" /></button><div className="source-note"><CircleHelp size={15} /><span>추천 결과는 지원 준비도를 안내합니다.<br />최종 지원 전 사람인 원문을 확인하세요.</span></div></div>
    </aside>
    <main className="main-area"><header className="topbar"><button className="menu-button" onClick={() => setMenuOpen(true)} aria-label="메뉴 열기"><Menu size={21} /></button><div className="breadcrumb"><span>JobPilot AI</span><ChevronRight size={15} /><strong>{activeItem.label}</strong></div><div className="top-actions"><button className="add-job"><Plus size={17} />공고 직접 등록</button><button className="bell" aria-label="알림"><Bell size={19} /><span /></button></div></header><section className="content"><Outlet /></section></main>
  </div></InterviewChatWidgetProvider>;
}

import { useState } from "react";
import { Bell, ChevronRight, CircleHelp, Github, Menu, Plus, Settings, X } from "lucide-react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { navigationItems } from "../shared/constants/navigation";

export function AppShell() {
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const activeItem = navigationItems.find((item) => item.path === location.pathname) ?? navigationItems[0];

  return (
    <div className="app-shell">
      <aside className={`sidebar ${menuOpen ? "open" : ""}`}>
        <div className="brand">
          <span className="brand-mark"><span>J</span></span>
          <div><strong>JobPilot AI</strong><small>career action coach</small></div>
          <button className="mobile-close" onClick={() => setMenuOpen(false)} aria-label="메뉴 닫기"><X size={19} /></button>
        </div>
        <div className="profile-card">
          <div className="avatar">김</div>
          <div><strong>김개발</strong><span>백엔드 개발자 준비 중</span></div>
          <button aria-label="프로필 설정"><Settings size={17} /></button>
        </div>
        <nav>
          <span className="nav-label">WORKSPACE</span>
          {navigationItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.path}
                to={item.path}
                end={item.path === "/"}
                className={({ isActive }) => isActive ? "nav-item active" : "nav-item"}
                onClick={() => setMenuOpen(false)}
              >
                <Icon size={19} /><span>{item.label}</span>{item.path === "/jobs" && <em>2</em>}
              </NavLink>
            );
          })}
        </nav>
        <div className="sidebar-bottom">
          <button className="github-connect"><Github size={18} /><span>GitHub 연결됨</span><span className="connected-dot" /></button>
          <div className="source-note"><CircleHelp size={15} /><span>추천은 지원 준비도이며<br />최종 자격은 원문 공고를 확인하세요.</span></div>
        </div>
      </aside>
      <main className="main-area">
        <header className="topbar">
          <button className="menu-button" onClick={() => setMenuOpen(true)} aria-label="메뉴 열기"><Menu size={21} /></button>
          <div className="breadcrumb"><span>JobPilot AI</span><ChevronRight size={15} /><strong>{activeItem.label}</strong></div>
          <div className="top-actions"><button className="add-job"><Plus size={17} />공고 직접 등록</button><button className="bell" aria-label="알림"><Bell size={19} /><span /></button></div>
        </header>
        <section className="content"><Outlet /></section>
      </main>
    </div>
  );
}

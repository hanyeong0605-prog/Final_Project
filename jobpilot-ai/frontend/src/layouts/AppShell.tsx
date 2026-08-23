import { type FormEvent, useEffect, useRef, useState } from "react";
import { ChevronDown, ChevronRight, CircleHelp, Github, LogOut, Menu, Plus, Search, ShieldCheck, UserRound, X } from "lucide-react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../features/auth/model/AuthContext";
import { getSubscriptionStatus } from "../features/subscription/api/subscriptionApi";
import { navigationGroups, navigationItems, type NavigationGroup } from "../shared/constants/navigation";
import { OnboardingModal } from "../features/profile/components/OnboardingModal";
import { SiteAssistantWidget } from "../features/assistant/components/SiteAssistantWidget";
import { SiteAssistantWidgetProvider } from "../features/assistant/model/SiteAssistantWidgetContext";
import { NotificationBell } from "../features/notifications/components/NotificationBell";

function isGroupActive(group: NavigationGroup, pathname: string) {
  if (group.path) return pathname === group.path;
  return group.items?.some((item) => pathname === item.path || pathname.startsWith(`${item.path}/`)) ?? false;
}

const megaMenuCopy: Record<string, string> = {
  "대시보드": "오늘의 채용 준비 현황과 ICT 시장 데이터를 한눈에 확인하세요.",
  "채용공고": "내 위치와 역량을 기준으로, 지금 확인할 공고를 빠르게 찾아보세요.",
  "역량 관리": "프로필부터 이력서, 진로 탐색까지 커리어 준비를 한곳에서 관리하세요.",
  "AI 모의면접": "실전 질문으로 연습하고, 기록을 통해 다음 면접을 더 잘 준비하세요.",
};

function BrandIdentity({ compact = false }: { compact?: boolean }) {
  return (
    <span className={`brand-identity${compact ? " compact" : ""}`}>
      <span className="brand-name brand-name-ko">잡아드림</span>
      <span className="brand-name brand-name-en">Job-A-Dream AI</span>
      {!compact && <small className="brand-tagline">career action coach</small>}
    </span>
  );
}

function AnimatedBrand() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [animationActive, setAnimationActive] = useState(false);

  const playAnimation = () => {
    const video = videoRef.current;
    if (!video || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    video.pause();
    video.currentTime = 0;
    setAnimationActive(true);
    void video.play().catch(() => setAnimationActive(false));
  };

  const resetAnimation = () => {
    const video = videoRef.current;
    if (video) {
      video.pause();
      video.currentTime = 0;
    }
    setAnimationActive(false);
  };

  return (
    <Link
      to="/"
      className={`desktop-brand brand-link${animationActive ? " is-animating" : ""}`}
      onPointerEnter={playAnimation}
      onPointerLeave={resetAnimation}
      onFocus={playAnimation}
      onBlur={resetAnimation}
    >
      <span className="brand-logo-scene">
        <span className="brand-mark brand-logo-letter">J</span>
        <span className="brand-bobo-stage" aria-hidden="true">
          <video
            ref={videoRef}
            className="brand-pounce-video"
            src="/mascot/animation/job-a-dream-logo-catch-cropped.webm"
            muted
            playsInline
            preload="auto"
          />
        </span>
      </span>
      <BrandIdentity />
    </Link>
  );
}

export function AppShell() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [openDesktopMenu, setOpenDesktopMenu] = useState<string | null>(null);
  const [openMobileMenu, setOpenMobileMenu] = useState<string | null>(null);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);
  const accountMenuRef = useRef<HTMLDivElement>(null);
  const location = useLocation();
  const navigate = useNavigate();
  const activeItem = navigationItems.find((item) => item.path === location.pathname)
    ?? navigationItems.find((item) => location.pathname.startsWith(`${item.path}/`))
    ?? navigationItems[0];
  const { member, logout } = useAuth();
  const [subscribed, setSubscribed] = useState(false);
  const hideSiteAssistant = location.pathname.startsWith("/resume");

  useEffect(() => {
    void getSubscriptionStatus().then((status) => setSubscribed(status.subscribed)).catch(() => setSubscribed(false));
  }, []);

  useEffect(() => {
    setMenuOpen(false);
    setOpenDesktopMenu(null);
    setOpenMobileMenu(null);
    setSearchOpen(false);
    setAccountMenuOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    const closeOnOutsidePress = (event: MouseEvent) => {
      if (!accountMenuRef.current?.contains(event.target as Node)) setAccountMenuOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setAccountMenuOpen(false);
    };
    document.addEventListener("mousedown", closeOnOutsidePress);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeOnOutsidePress);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, []);

  const closeMenu = () => setMenuOpen(false);
  const searchResults = navigationItems.filter((item) => item.label.toLocaleLowerCase().includes(searchTerm.trim().toLocaleLowerCase())).slice(0, 6);
  const submitGlobalSearch = (event: FormEvent) => {
    event.preventDefault();
    const query = searchTerm.trim();
    if (!query) return;
    const directMatch = navigationItems.find((item) => item.label === query);
    navigate(directMatch?.path ?? `/job-postings?query=${encodeURIComponent(query)}`);
  };
  const logoutFromAccountMenu = () => {
    logout();
    setAccountMenuOpen(false);
    navigate("/login", { replace: true });
  };
  return (
    <SiteAssistantWidgetProvider>
      <div className="app-shell">
        <OnboardingModal />
        {!hideSiteAssistant && <SiteAssistantWidget />}

        {menuOpen && <button className="mobile-menu-backdrop" aria-label="메뉴 닫기" onClick={closeMenu} />}
        <aside className={`sidebar ${menuOpen ? "open" : ""}`} aria-label="모바일 메뉴">
          <div className="brand mobile-sidebar-brand">
            <Link to="/" className="brand-link" onClick={closeMenu}>
              <span className="brand-mark">J</span>
              <BrandIdentity />
            </Link>
            <button className="mobile-close" onClick={closeMenu} aria-label="메뉴 닫기"><X size={19} /></button>
          </div>

          <div className="profile-card">
            <div className="avatar">{member?.nickname?.slice(0, 1) ?? "J"}</div>
            <div><strong>{member?.nickname}{member?.role === "ADMIN" ? <span className="subscription-badge active sidebar-subscription-badge">관리자</span> : subscribed && <span className="subscription-badge active sidebar-subscription-badge">구독중</span>}</strong><span>{member?.email}</span></div>
            <button aria-label="로그아웃" title="로그아웃" onClick={logout}><LogOut size={17} /></button>
          </div>

          <nav className="mobile-navigation">
            <span className="nav-label">MENU</span>
            {navigationGroups.map((group) => {
              const Icon = group.icon;
              const expandable = Boolean(group.items?.length);
              const active = isGroupActive(group, location.pathname);
              const expanded = openMobileMenu === group.label || active;
              if (!expandable && group.path && Icon) {
                return <NavLink key={group.label} to={group.path} className={({ isActive }) => isActive ? "nav-item active" : "nav-item"} onClick={closeMenu}><Icon size={19} /><span>{group.label}</span></NavLink>;
              }
              return <div className={`mobile-nav-group ${expanded ? "expanded" : ""}`} key={group.label}>
                <button className={`nav-item mobile-group-trigger ${active ? "active" : ""}`} onClick={() => setOpenMobileMenu(expanded ? null : group.label)}>{Icon && <Icon size={19} />}<span>{group.label}</span><ChevronDown size={16} /></button>
                <div className="mobile-submenu">{group.items?.map((item) => <NavLink key={item.path} to={item.path} className={({ isActive }) => isActive ? "mobile-submenu-item active" : "mobile-submenu-item"} onClick={closeMenu}>{item.label}</NavLink>)}</div>
              </div>;
            })}
          </nav>

          <div className="sidebar-bottom">
            <button className="github-connect"><Github size={18} /><span>GitHub 연결</span><span className="connected-dot" /></button>
            <div className="source-note"><CircleHelp size={15} /><span>추천 결과는 지원 준비도를 안내합니다.<br />최종 지원 전 채용 원문을 확인하세요.</span></div>
          </div>
        </aside>

        <main className="main-area">
          <header className="topbar" onMouseLeave={() => setOpenDesktopMenu(null)}>
            <button className="menu-button" onClick={() => setMenuOpen(true)} aria-label="메뉴 열기"><Menu size={21} /></button>
            <AnimatedBrand />

            <nav className="desktop-navigation" aria-label="주요 메뉴">
              {navigationGroups.map((group) => {
                const Icon = group.icon;
                const active = isGroupActive(group, location.pathname);
                if (group.path) return <NavLink key={group.label} to={group.path} className={({ isActive }) => isActive ? "desktop-nav-link active" : "desktop-nav-link"}>{Icon && <Icon size={16} />}{group.label}</NavLink>;
                const open = openDesktopMenu === group.label;
                return <div className="desktop-nav-group" key={group.label} onMouseEnter={() => setOpenDesktopMenu(group.label)}>
                  <button className={`desktop-nav-link ${active ? "active" : ""}`} aria-expanded={open} onClick={() => setOpenDesktopMenu(open ? null : group.label)}>{Icon && <Icon size={16} />}{group.label}<ChevronDown size={15} /></button>
                </div>;
              })}
            </nav>

            <div className="top-actions">
              <div className="breadcrumb"><span>Job-A-Dream AI</span><ChevronRight size={15} /><strong>{activeItem.label}</strong></div>
              <form className="global-search-inline" onSubmit={submitGlobalSearch}><Search size={16} /><input value={searchTerm} onFocus={() => setSearchOpen(true)} onChange={(event) => { setSearchTerm(event.target.value); setSearchOpen(true); }} placeholder="통합검색" aria-label="통합검색" /><button type="submit" aria-label="검색 실행"><ChevronRight size={15} /></button></form>
              <button className="add-job"><Plus size={17} />공고 직접 등록</button>
              <NotificationBell />
              {member?.role === "ADMIN" && <NavLink to="/admin" className="admin-page-link"><ShieldCheck size={16} />관리자 페이지</NavLink>}
              <div className="topbar-account-menu" ref={accountMenuRef}>
                <button className="topbar-account" type="button" aria-label="계정 메뉴" aria-expanded={accountMenuOpen} onClick={() => setAccountMenuOpen((open) => !open)}>{member?.nickname?.slice(0, 1) ?? "J"}</button>
                {accountMenuOpen && <div className="account-popover" role="menu">
                  <div className="account-popover-head"><span>{member?.nickname ?? "사용자"}</span><small>{member?.email}</small></div>
                  <NavLink to="/account" role="menuitem" onClick={() => setAccountMenuOpen(false)}><UserRound size={16} />마이페이지</NavLink>
                  {member?.role === "ADMIN" && <NavLink to="/admin" role="menuitem" onClick={() => setAccountMenuOpen(false)}><ShieldCheck size={16} />관리자 페이지</NavLink>}
                  <button type="button" role="menuitem" onClick={logoutFromAccountMenu}><LogOut size={16} />로그아웃</button>
                </div>}
              </div>
            </div>
            {openDesktopMenu && (() => {
              const group = navigationGroups.find((item) => item.label === openDesktopMenu);
              if (!group?.items?.length) return null;
              const Icon = group.icon;
              return <section className="desktop-mega-menu" aria-label={`${group.label} 하위 메뉴`} onMouseEnter={() => setOpenDesktopMenu(group.label)}>
                <div className="desktop-mega-inner">
                  <div className="mega-menu-intro">{Icon && <span><Icon size={21} /></span>}<div><strong>{group.label}</strong><p>{megaMenuCopy[group.label]}</p></div></div>
                  <div className="mega-menu-links">{group.items.map((item) => { const ItemIcon = item.icon; return <NavLink key={item.path} to={item.path} className={({ isActive }) => isActive ? "mega-menu-link active" : "mega-menu-link"}><ItemIcon size={18} /><div><strong>{item.label}</strong><small>{item.label} 바로가기</small></div><ChevronRight size={16} /></NavLink>; })}</div>
                </div>
              </section>;
            })()}
            {searchOpen && <section className="global-search-panel" aria-label="통합검색">
              <form onSubmit={submitGlobalSearch}>
                <Search size={19} /><input autoFocus value={searchTerm} onChange={(event) => setSearchTerm(event.target.value)} placeholder="메뉴, 기능 또는 채용공고 키워드를 검색하세요" /><button type="button" onClick={() => setSearchOpen(false)} aria-label="통합검색 닫기"><X size={18} /></button>
              </form>
              <div className="global-search-results">
                {searchTerm.trim() && <button type="button" className="global-search-job" onClick={() => navigate(`/job-postings?query=${encodeURIComponent(searchTerm.trim())}`)}><Search size={16} /><span><b>‘{searchTerm.trim()}’ 채용공고 검색</b><small>회사명·공고명·기술·지역에서 찾기</small></span><ChevronRight size={16} /></button>}
                {searchResults.map((item) => { const Icon = item.icon; return <button type="button" key={item.path} onClick={() => navigate(item.path)}><Icon size={16} /><span><b>{item.label}</b><small>페이지로 이동</small></span><ChevronRight size={16} /></button>; })}
                {!searchTerm.trim() && <p>원하는 메뉴 또는 채용공고 키워드를 입력하세요.</p>}
              </div>
            </section>}
          </header>
          <section className="content"><Outlet /></section>
        </main>
      </div>
    </SiteAssistantWidgetProvider>
  );
}

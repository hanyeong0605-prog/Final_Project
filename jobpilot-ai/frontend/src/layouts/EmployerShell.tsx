import { BriefcaseBusiness, LayoutDashboard, LogOut, Menu, UserRound, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useEmployerAuth } from "../features/employer/model/EmployerAuthContext";
import { EmployerNotificationBell } from "../features/employer/components/EmployerNotificationBell";
import { BrandLogo } from "../shared/components/BrandLogo";
import { SiteFooter } from "../shared/components/SiteFooter";

const employerNavigation = [
  { path: "/employer/dashboard", label: "대시보드", icon: LayoutDashboard },
  { path: "/employer/postings", label: "공고 관리", icon: BriefcaseBusiness },
];

export function EmployerShell() {
  const { employer, logout } = useEmployerAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const menuRef = useRef<HTMLDivElement>(null);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);

  useEffect(() => {
    setMobileOpen(false);
    setAccountOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setAccountOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  if (!employer) return null;

  const signOut = () => {
    logout();
    navigate("/employer/login", { replace: true });
  };

  return (
    <div className="employer-shell">
      {mobileOpen && <button className="employer-mobile-backdrop" aria-label="메뉴 닫기" onClick={() => setMobileOpen(false)} />}
      <header className="employer-topbar">
        <button className="employer-menu-button" aria-label="메뉴 열기" onClick={() => setMobileOpen(true)}><Menu /></button>
        <Link className="employer-brand brand-link" to="/employer">
          <BrandLogo compact />
          <span>RECRUITER</span>
        </Link>
        <nav className={mobileOpen ? "open" : ""} aria-label="기업회원 메뉴">
          <button className="employer-nav-close" aria-label="메뉴 닫기" onClick={() => setMobileOpen(false)}><X /></button>
          {employerNavigation.map(({ path, label, icon: Icon }) => (
            <NavLink key={path} to={path} className={({ isActive }) => isActive ? "active" : ""}><Icon size={17} />{label}</NavLink>
          ))}
        </nav>
        <div className="employer-actions">
          <EmployerNotificationBell />
          <div className="topbar-account-menu" ref={menuRef}>
            <button className="employer-account-button" onClick={() => setAccountOpen((value) => !value)}>{employer.companyName.slice(0, 1)}</button>
            {accountOpen && <div className="account-popover employer-account-popover">
              <div className="account-popover-head"><span>{employer.companyName}</span><small>{employer.managerName} 담당자</small></div>
              <Link to="/employer/account"><UserRound size={16} />기업 마이페이지</Link>
              <div className="employer-account-status"><span>기업 승인: {employer.status}</span></div>
              <button type="button" onClick={signOut}><LogOut size={16} />로그아웃</button>
            </div>}
          </div>
        </div>
      </header>
      <main className="employer-content"><Outlet /></main>
      <SiteFooter />
    </div>
  );
}

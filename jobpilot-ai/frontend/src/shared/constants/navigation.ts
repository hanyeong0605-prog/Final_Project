import type { LucideIcon } from "lucide-react";
import {
  BarChart3,
  BriefcaseBusiness,
  CalendarDays,
  ClipboardCheck,
  Code2,
  FileText,
  History,
  LayoutDashboard,
  MapPin,
  Mic,
  Sparkles,
  Target,
  UserRound,
  Pen,
  Notebook,
  Star,
  Heart,
  
} from "lucide-react";

export interface NavigationItem {
  path: string;
  label: string;
  icon: LucideIcon;
}

export interface NavigationGroup {
  label: string;
  icon?: LucideIcon;
  path?: string;
  items?: NavigationItem[];
}

export const navigationGroups: NavigationGroup[] = [
  {
    label: "대시보드",
    icon: LayoutDashboard,
    items: [
      { path: "/dashboard", label: "대시보드", icon: LayoutDashboard },
      { path: "/statistics", label: "ICT 관련 통계", icon: BarChart3 },
      { path: "/planner", label: "나의 플래너", icon: CalendarDays },
    ],
  },
  {
    label: "채용공고",
    icon: BriefcaseBusiness,
    items: [
      { path: "/job-postings", label: "전체 채용공고", icon: BriefcaseBusiness },
      { path: "/locationjobs", label: "우리 동네 채용공고", icon: MapPin },
      { path: "/jobs", label: "맞춤 채용공고", icon: Target },
    ],
  },
  {
    label: "역량 관리",
    icon: Target,
    items: [
      { path: "/profile", label: "역량 프로필", icon: Target },
      { path: "/opportunities", label: "성장 기회 추천", icon: Sparkles },
      { path: "/resume", label: "이력서 작성 도우미", icon: FileText },
      { path: "/question", label: "진로검사·글쓰기 도구", icon: ClipboardCheck },
      { path: "/repository-analysis", label: "GitHub 코드 분석", icon: Code2 },
    ],
  },
  {
    label: "AI 모의면접",
    icon: Mic,
    items: [
      { path: "/mock-interview", label: "AI 모의면접", icon: Mic },
      { path: "/timeline", label: "개인 타임라인", icon: History },
    ],
  },
  { path: "/account", label: "마이페이지", icon: UserRound },

{
  label:"게시판",
  icon : Notebook,
  items:[
  { path: "/free-board", label: "자유 게시판", icon: Pen },
  { path: "/review-board", label: "기업 후기 게시판", icon: Star },
  { path: "/work-board", label: "현업자 게시판", icon: Heart },
],
}
  
];

export const utilityNavigationItems: NavigationItem[] = [
  { path: "/skill-relation", label: "채용공고 워드클라우드", icon: BarChart3 },
];

export const navigationItems = [
  { path: "/", label: "홈", icon: LayoutDashboard },
  ...navigationGroups.flatMap((group) => group.items ?? (group.path && group.icon ? [{ path: group.path, label: group.label, icon: group.icon }] : [])),
  ...utilityNavigationItems,
];

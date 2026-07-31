import type { LucideIcon } from "lucide-react";
import { BriefcaseBusiness, CalendarDays, Code2, LayoutDashboard, Sparkles, Target } from "lucide-react";

export interface NavigationItem {
  path: string;
  label: string;
  icon: LucideIcon;
}

export const navigationItems: NavigationItem[] = [
  { path: "/", label: "대시보드", icon: LayoutDashboard },
  { path: "/jobs", label: "맞춤 채용공고", icon: BriefcaseBusiness },
  { path: "/opportunities", label: "성장 기회 추천", icon: Sparkles },
  { path: "/planner", label: "나의 플래너", icon: CalendarDays },
  { path: "/profile", label: "역량 프로필", icon: Target },
  { path: "/repository-analysis", label: "GitHub 코드 분석", icon: Code2 },
];

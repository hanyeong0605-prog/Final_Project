import type { LucideIcon } from "lucide-react";
import { BriefcaseBusiness, CalendarDays, ClipboardCheck, Code2, FileText, History, LayoutDashboard, ListFilter, MapPin, Mic, Sparkles, Target, UserRound,BarChart3} from "lucide-react";
import { Cpu } from "lucide-react";

export interface NavigationItem {
  path: string;
  label: string;
  icon: LucideIcon;
}

export const navigationItems: NavigationItem[] = [
  { path: "/", label: "대시보드", icon: LayoutDashboard },
  { path: "/statistics", label: "ICT 관련 통계", icon: BarChart3 },
  { path: "/job-postings", label: "전체 채용공고", icon: ListFilter },
  { path: "/locationjobs", label: "우리 동네 채용공고", icon: MapPin },
  { path: "/jobs", label: "맞춤 채용공고", icon: BriefcaseBusiness },
  { path: "/opportunities", label: "성장 기회 추천", icon: Sparkles },
  { path: "/planner", label: "나의 플래너", icon: CalendarDays },
  { path: "/profile", label: "역량 프로필", icon: Target },
  { path: "/resume", label: "이력서 작성 도우미", icon: FileText },
  { path: "/mock-interview", label: "AI모의면접", icon: Mic },
  { path: "/timeline", label: "개인 타임라인", icon: History },
  { path: "/question", label: "진로검사·글쓰기 도구", icon: ClipboardCheck },
  { path: "/account", label: "마이페이지", icon: UserRound },
  { path: "/repository-analysis", label: "GitHub 코드 분석", icon: Code2 },
  { path: "/skill-relation", label: "채용공고 워드클라우드", icon: Cpu },
];

import { Bookmark, CalendarDays, ChevronRight, Sparkles } from "lucide-react";
import type { Opportunity } from "../model/opportunity.types";

interface OpportunityCardProps {
  item: Opportunity;
  interested: boolean;
  onInterest: () => void;
}

export function OpportunityCard({ item, interested, onInterest }: OpportunityCardProps) {
  const tone = item.type === "교육" ? "blue" : item.type === "자격증" ? "purple" : "orange";
  return <article className="opportunity-card"><div className="opportunity-top"><span className={`type-badge ${tone}`}>{item.type}</span><button className={interested ? "bookmark active" : "bookmark"} onClick={onInterest} aria-label="관심 등록"><Bookmark size={19} fill={interested ? "currentColor" : "none"} /></button></div><span className="organization">{item.organization}</span><h2>{item.title}</h2><p className="period"><CalendarDays size={15} />{item.period}</p><div className="reason"><Sparkles size={16} /><p>{item.reason}</p></div><div className="skills">{item.tags.map((tag) => <span key={tag}>{tag}</span>)}</div><div className="opportunity-footer"><span>신청 마감 <strong>{item.deadline}</strong></span><a href="#source">상세 보기 <ChevronRight size={15} /></a></div></article>;
}

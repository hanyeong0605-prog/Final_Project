import { Bookmark, CalendarDays, ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import type { Opportunity } from "../model/opportunity.types";

interface OpportunityCardProps {
  item: Opportunity;
  interested: boolean;
  onInterest: () => void;
}

export function OpportunityCard({ item, interested, onInterest }: OpportunityCardProps) {
  const tone = item.type === "교육" ? "blue" : item.type === "자격증" ? "purple" : "orange";
  const closed = item.status === "CLOSED" || item.status === "EXPIRED";
  return <article className={`opportunity-card ${closed ? "closed" : ""}`}><div className="opportunity-top"><span className={`type-badge ${closed ? "closed-badge" : tone}`}>{closed ? "신청 마감" : item.type}</span><button className={interested ? "bookmark active" : "bookmark"} onClick={onInterest} aria-label="관심 등록"><Bookmark size={19} fill={interested ? "currentColor" : "none"} /></button></div><span className="organization">{item.organization}</span><h2>{item.title}</h2><p className="period"><CalendarDays size={17} />{item.period}</p><div className="skills">{item.tags.map((tag) => <span key={tag}>{tag}</span>)}</div><div className="opportunity-footer"><span>{closed ? "신청이 마감된 과정" : "훈련 시작 임박"}</span><Link to={`/opportunities/${item.id}`}>상세 보기 <ChevronRight size={16} /></Link></div></article>;
}

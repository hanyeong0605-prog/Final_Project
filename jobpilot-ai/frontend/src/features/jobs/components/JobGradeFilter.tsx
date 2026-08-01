import { Search } from "lucide-react";
import type { RecommendationLevel } from "../model/job.types";

interface JobGradeFilterProps {
  grade: RecommendationLevel | "ALL";
  onGradeChange: (grade: RecommendationLevel | "ALL") => void;
}

const filters: { id: RecommendationLevel | "ALL"; label: string }[] = [
  { id: "ALL", label: "전체" }, { id: "APPLY_NOW", label: "지금도 지원해볼 만함" }, { id: "CHALLENGE_AFTER_GAPS", label: "요건 보완 후 도전 가능" }, { id: "DIFFICULT_NOW", label: "현재는 지원이 어려움" },
];

export function JobGradeFilter({ grade, onGradeChange }: JobGradeFilterProps) {
  return <section className="filter-panel"><div className="search-box"><Search size={18} /><input placeholder="회사명, 직무, 기술로 검색" /><button>검색</button></div><div className="filter-row"><span>지원 준비도</span>{filters.map((filter) => <button key={filter.id} className={grade === filter.id ? "filter active" : "filter"} onClick={() => onGradeChange(filter.id)}>{filter.label}</button>)}</div></section>;
}

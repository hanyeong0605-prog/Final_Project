import { Search } from "lucide-react";
import type { MatchGrade } from "../model/job.types";

interface JobGradeFilterProps {
  grade: MatchGrade | "ALL";
  onGradeChange: (grade: MatchGrade | "ALL") => void;
}

const filters: { id: MatchGrade | "ALL"; label: string }[] = [
  { id: "ALL", label: "전체" }, { id: "READY_TO_APPLY", label: "지원 조건 충족 가능" }, { id: "NEEDS_IMPROVEMENT", label: "보완 후 도전 가능" }, { id: "INSUFFICIENT_EVIDENCE", label: "현재 근거 부족" },
];

export function JobGradeFilter({ grade, onGradeChange }: JobGradeFilterProps) {
  return <section className="filter-panel"><div className="search-box"><Search size={18} /><input placeholder="회사명, 직무, 기술로 검색" /><button>검색</button></div><div className="filter-row"><span>지원 준비도</span>{filters.map((filter) => <button key={filter.id} className={grade === filter.id ? "filter active" : "filter"} onClick={() => onGradeChange(filter.id)}>{filter.label}</button>)}</div></section>;
}

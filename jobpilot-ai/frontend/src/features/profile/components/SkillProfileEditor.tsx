import { Search, X } from "lucide-react";
import { useEffect, useState } from "react";
import { searchSkillCatalog } from "../api/memberSkillsApi";
import type { MemberSkill, SkillCatalogItem, SkillLevel } from "../model/memberSkill.types";

const levelLabels: Record<SkillLevel, string> = {
  LEARNING: "학습 중",
  PROJECT: "프로젝트 경험",
  INTERNSHIP: "인턴·실무 체험",
  PROFESSIONAL: "실무 경력",
};

const categoryLabels: Record<string, string> = {
  LANGUAGE: "언어", BACKEND: "백엔드", FRONTEND: "프론트엔드", DATABASE: "데이터베이스",
  CLOUD_DEVOPS: "클라우드·DevOps", AI_DATA: "AI·데이터", MOBILE: "모바일", TEST: "테스트", TOOL: "도구",
};

export function SkillProfileEditor({ value, onChange }: { value: MemberSkill[]; onChange: (value: MemberSkill[]) => void }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SkillCatalogItem[]>([]);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    const keyword = query.trim();
    if (keyword.length < 2) { setResults([]); setSearching(false); return; }
    const timer = window.setTimeout(() => {
      setSearching(true);
      void searchSkillCatalog(keyword)
        .then((items) => setResults(items.filter((item) => !value.some((skill) => skill.skillId === item.id))))
        .catch(() => setResults([]))
        .finally(() => setSearching(false));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [query, value]);

  const add = (item: SkillCatalogItem) => {
    if (value.some((skill) => skill.skillId === item.id)) return;
    onChange([...value, { skillId: item.id, skillName: item.name, category: item.category, selfReportedLevel: "LEARNING", note: null }]);
    setQuery("");
    setResults([]);
  };

  const update = (skillId: number, patch: Partial<MemberSkill>) => {
    onChange(value.map((skill) => skill.skillId === skillId ? { ...skill, ...patch } : skill));
  };

  return <section className="skill-profile-editor">
    <div className="skill-editor-intro">
      <div><h3>보유 기술스택</h3><p>기술명을 검색해 추가하고, 실제 경험 수준을 선택해 주세요.</p></div>
      <span>{value.length}/30</span>
    </div>
    <div className="skill-search-wrap">
      <Search size={16} aria-hidden="true" />
      <input value={query} onChange={(e) => setQuery(e.target.value)} maxLength={80} placeholder="예: Spring, React, AWS, Python" aria-label="보유 기술 검색" />
      {searching && <small>검색 중</small>}
      {results.length > 0 && <div className="skill-search-results" role="listbox">
        {results.map((item) => <button type="button" key={item.id} onClick={() => add(item)}>
          <strong>{item.name}</strong><span>{categoryLabels[item.category] ?? item.category}</span>
        </button>)}
      </div>}
    </div>
    {query.trim().length === 1 && <p className="skill-search-guide">두 글자 이상 입력하면 표준 기술 목록을 검색합니다.</p>}
    {value.length === 0 ? <div className="skill-empty">아직 선택한 기술이 없습니다. 위 검색창에서 추가해 주세요.</div> :
      <div className="selected-skill-list">
        {value.map((skill) => <article className="selected-skill" key={skill.skillId}>
          <div className="selected-skill-name"><strong>{skill.skillName}</strong><span>{categoryLabels[skill.category] ?? skill.category}</span></div>
          <label>경험 수준<select value={skill.selfReportedLevel} onChange={(e) => update(skill.skillId, { selfReportedLevel: e.target.value as SkillLevel })}>
            {(Object.keys(levelLabels) as SkillLevel[]).map((level) => <option key={level} value={level}>{levelLabels[level]}</option>)}
          </select></label>
          <label className="selected-skill-note">근거·메모<input value={skill.note ?? ""} onChange={(e) => update(skill.skillId, { note: e.target.value || null })} maxLength={300} placeholder="예: 쇼핑몰 API 프로젝트에서 사용" /></label>
          <button type="button" className="remove-skill" onClick={() => onChange(value.filter((item) => item.skillId !== skill.skillId))} aria-label={`${skill.skillName} 삭제`}><X size={16} /></button>
        </article>)}
      </div>}
  </section>;
}

import { Heart, Search, UsersRound } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getEmployerTalentFavorites, getEmployerTalents, toggleEmployerTalentFavorite, type EmployerTalent } from "../features/employer/api/employerTalentApi";
import { PageHeading } from "../shared/components/PageHeading";

export function EmployerDashboardPage() {
  const navigate = useNavigate(); const [query, setQuery] = useState(""); const [items, setItems] = useState<EmployerTalent[]>([]); const [favorites, setFavorites] = useState<number[]>([]);
  const load = () => getEmployerTalents(query).then(setItems).catch(() => setItems([]));
  useEffect(() => { void load(); void getEmployerTalentFavorites().then((data) => setFavorites(data.map((item) => item.memberId))).catch(() => {}); }, []);
  const favorite = (memberId: number) => { void toggleEmployerTalentFavorite(memberId).then(({ favorite: active }) => setFavorites((current) => active ? [...new Set([...current, memberId])] : current.filter((id) => id !== memberId))); };
  return <><PageHeading eyebrow="TALENT DASHBOARD" title="공개 인재 대시보드" body="역량과 스펙 공개에 동의한 회원만 조회할 수 있습니다." /><section className="panel employer-talent-panel"><div className="employer-talent-toolbar"><div><UsersRound size={19} /><strong>공개 인재 · {items.length}명</strong></div><label><Search size={16} /><input value={query} onChange={(e) => setQuery(e.target.value)} onKeyDown={(e) => e.key === "Enter" && load()} placeholder="직무·기술·지역 검색" /></label></div>{items.length === 0 ? <div className="data-empty"><UsersRound size={28} /><strong>공개 중인 인재 정보가 없습니다.</strong><p>일반회원이 스펙 공개를 켜면 이곳에 표시됩니다.</p></div> : <div className="employer-talent-list">{items.map((item) => <article key={item.memberId}><button type="button" className="employer-talent-open" onClick={() => navigate(`/employer/talents/${item.memberId}`)}><strong>{item.nickname}</strong><span>{item.targetJobFamily} · {item.targetRole}</span><small>{item.preferredLocations || "희망 지역 미설정"} · {item.skills.slice(0, 4).join(" · ")}</small><em>스펙 상세 보기</em></button><button type="button" className={`talent-favorite-button ${favorites.includes(item.memberId) ? "active" : ""}`} onClick={() => favorite(item.memberId)} aria-label="관심 인재"><Heart size={19} fill={favorites.includes(item.memberId) ? "currentColor" : "none"} /></button></article>)}</div>}</section></>;
}

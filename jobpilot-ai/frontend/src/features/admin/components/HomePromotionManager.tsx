import { BookOpen, GraduationCap, Search, Trash2 } from "lucide-react";
import { type FormEvent, useEffect, useState } from "react";
import { listRecommendedBooks, type RecommendedBook } from "../../opportunities/api/bookRecommendationsApi";
import {
  createAdminHomePromotion, deleteAdminHomePromotion, getAdminHomePromotions, getAdminTrainingPromotionCandidates,
  type AdminHomePromotion, type AdminTrainingPromotionCandidate,
} from "../api/adminApi";

export function HomePromotionManager() {
  const [items, setItems] = useState<AdminHomePromotion[]>([]);
  const [trainingQuery, setTrainingQuery] = useState("");
  const [trainings, setTrainings] = useState<AdminTrainingPromotionCandidate[]>([]);
  const [bookQuery, setBookQuery] = useState("");
  const [books, setBooks] = useState<RecommendedBook[]>([]);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const reload = async () => setItems(await getAdminHomePromotions());
  const searchTrainings = async () => setTrainings((await getAdminTrainingPromotionCandidates(trainingQuery)).content);
  useEffect(() => { void reload().catch(() => setError("홈 광고 목록을 불러오지 못했습니다.")); void searchTrainings().catch(() => undefined); }, []);
  const count = (slotType: "TRAINING" | "BOOK") => items.filter((item) => item.slotType === slotType).length;
  const add = async (payload: Parameters<typeof createAdminHomePromotion>[0]) => {
    setError(""); setMessage("");
    try { await createAdminHomePromotion(payload); await reload(); setMessage("홈 광고에 게시했습니다."); }
    catch (e) { setError(e instanceof Error ? e.message : "홈 광고 게시에 실패했습니다."); }
  };
  const remove = async (item: AdminHomePromotion) => {
    setError(""); setMessage("");
    try { await deleteAdminHomePromotion(item.id); await reload(); setMessage("홈 광고에서 내렸습니다."); }
    catch (e) { setError(e instanceof Error ? e.message : "삭제에 실패했습니다."); }
  };
  const searchBooks = async (event: FormEvent) => {
    event.preventDefault(); setError("");
    try { setBooks((await listRecommendedBooks({ query: bookQuery, size: 10 })).items); }
    catch { setError("도서 검색 결과를 불러오지 못했습니다."); }
  };
  const published = (slotType: "TRAINING" | "BOOK", key: string) => items.some((item) => item.slotType === slotType && item.targetUrl === key);
  return <section className="panel admin-panel home-promotion-admin">
    <div className="admin-panel-heading"><div><span className="eyebrow">HOME AD PLACEMENT</span><h2>홈 성장기회 광고 관리</h2><p>고용24 훈련과정 2개와 도서 2개를 골라 홈 화면에 게시합니다.</p></div></div>
    {message && <p className="admin-inline-notice">{message}</p>}{error && <p className="admin-inline-error">{error}</p>}
    <div className="admin-promotion-current">{(["TRAINING", "BOOK"] as const).map((slotType) => <div key={slotType}><strong>{slotType === "TRAINING" ? "고용24 훈련과정" : "추천 도서"} <b>{count(slotType)} / 2</b></strong>{items.filter((item) => item.slotType === slotType).map((item) => <article key={item.id}><img src={item.imageUrl || ""} alt="" /><span><b>{item.title}</b><small>{item.provider || item.description || ""}</small></span><button className="icon-button danger" onClick={() => void remove(item)} title="홈 광고에서 내리기"><Trash2 size={15} /></button></article>)}{count(slotType) === 0 && <small>아직 선택한 항목이 없습니다.</small>}</div>)}</div>
    <div className="admin-promotion-searches">
      <div><h3><GraduationCap size={17} /> 고용24 훈련과정 검색</h3><form onSubmit={(event) => { event.preventDefault(); void searchTrainings(); }}><input value={trainingQuery} onChange={(event) => setTrainingQuery(event.target.value)} placeholder="과정명·기관명 검색" /><button className="outline-button"><Search size={14} /> 검색</button></form><div className="admin-promotion-results">{trainings.map((item) => <article key={item.id}><img src={item.thumbnailUrl || ""} alt="" /><span><b>{item.title}</b><small>{[item.organization, item.period].filter(Boolean).join(" · ")}</small></span><button className="outline-button" disabled={count("TRAINING") >= 2 || published("TRAINING", item.targetUrl)} onClick={() => void add({ slotType: "TRAINING", sourceKey: String(item.id), title: item.title, provider: item.organization, description: item.period, imageUrl: item.thumbnailUrl, targetUrl: item.targetUrl })}>{published("TRAINING", item.targetUrl) ? "게시됨" : "광고에 게시"}</button></article>)}</div></div>
      <div><h3><BookOpen size={17} /> 도서 검색</h3><form onSubmit={(event) => void searchBooks(event)}><input value={bookQuery} onChange={(event) => setBookQuery(event.target.value)} placeholder="도서명·저자·기술 검색" /><button className="outline-button"><Search size={14} /> 검색</button></form><div className="admin-promotion-results">{books.map((item) => <article key={item.isbn13 || item.link}><img src={item.coverUrl || ""} alt="" /><span><b>{item.title}</b><small>{[item.author, item.publisher].filter(Boolean).join(" · ")}</small></span><button className="outline-button" disabled={count("BOOK") >= 2 || published("BOOK", item.link)} onClick={() => void add({ slotType: "BOOK", sourceKey: item.isbn13 || item.link, title: item.title, provider: [item.author, item.publisher].filter(Boolean).join(" · "), description: item.description, imageUrl: item.coverUrl, targetUrl: item.link })}>{published("BOOK", item.link) ? "게시됨" : "광고에 게시"}</button></article>)}</div></div>
    </div>
  </section>;
}

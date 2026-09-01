import { BookOpen, ExternalLink, GraduationCap, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getHomePromotions, type HomePromotion } from "../api/homePromotionsApi";

function PromotionCard({ item }: { item: HomePromotion }) {
  const inner = <>
    <div className="home-promotion-image">{item.imageUrl ? <img src={item.imageUrl} alt="" /> : item.slotType === "BOOK" ? <BookOpen size={28} /> : <GraduationCap size={28} />}</div>
    <div className="home-promotion-copy"><span>{item.slotType === "BOOK" ? "추천 도서" : "고용24 훈련과정"}</span><strong>{item.title}</strong><p>{item.provider || item.description || "잡아드림이 추천하는 성장 기회"}</p></div>
    <ExternalLink size={16} aria-hidden="true" />
  </>;
  return item.targetUrl.startsWith("/")
    ? <Link className="home-promotion-card" to={item.targetUrl}>{inner}</Link>
    : <a className="home-promotion-card" href={item.targetUrl} target="_blank" rel="noreferrer">{inner}</a>;
}

export function HomeGrowthPromotions() {
  const [items, setItems] = useState<HomePromotion[]>([]);
  useEffect(() => { void getHomePromotions().then(setItems).catch(() => setItems([])); }, []);
  const training = items.filter((item) => item.slotType === "TRAINING").slice(0, 2);
  const books = items.filter((item) => item.slotType === "BOOK").slice(0, 2);
  if (!items.length) return null;
  // The items arrive after HomePage's initial scroll-reveal observer is registered.
  // Keeping this async section out of that observer prevents it from remaining transparent.
  return <section className="home-growth-promotions">
    <div className="home-section-heading"><div><span className="eyebrow">CURATED GROWTH</span><h2>잡아드림이 추천하는 성장 기회</h2><p className="home-section-description">관리자가 고른 고용24 훈련과정과 IT 취업 도서를 확인해 보세요.</p></div><Sparkles size={22} /></div>
    <div className="home-promotion-groups">
      <div><h3><GraduationCap size={17} /> 고용24 훈련과정</h3><div className="home-promotion-grid">{training.map((item) => <PromotionCard key={item.id} item={item} />)}</div></div>
      <div><h3><BookOpen size={17} /> 추천 도서</h3><div className="home-promotion-grid">{books.map((item) => <PromotionCard key={item.id} item={item} />)}</div></div>
    </div>
  </section>;
}

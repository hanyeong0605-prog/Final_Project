import { useEffect } from "react";
import { ArrowRight, BriefcaseBusiness, CheckCircle2, Mic, Sparkles, Target } from "lucide-react";
import { Link } from "react-router-dom";
import { IctStatisticsPanel } from "../features/statistics/components/IctStatisticsPanel";
import { HomeJobCarousels } from "../features/job-postings/components/HomeJobCarousels";
import { WordCloudSection } from "../features/word-cloud/components/WordCloudSection";

const quickLinks = [
  { to: "/dashboard", icon: BriefcaseBusiness, title: "맞춤 채용 분석", body: "내 역량과 공고 요구사항을 비교해 지원 준비도를 확인하세요." },
  { to: "/profile", icon: Target, title: "역량 프로필", body: "기술, 학력, 자격증을 등록하고 부족한 역량을 찾아보세요." },
  { to: "/mock-interview", icon: Mic, title: "AI 모의면접", body: "질문·음성·표정 분석으로 실제 면접처럼 연습하세요." },
];

const insightBars = [
  { label: "실무 프로젝트 경험", value: 86 },
  { label: "기술 스택 역량", value: 78 },
  { label: "직무 적합성", value: 71 },
  { label: "자격·교육 이수", value: 63 },
];

export function HomePage() {
  useEffect(() => {
    const sections = Array.from(document.querySelectorAll<HTMLElement>("[data-scroll-reveal]"));
    const observer = new IntersectionObserver((entries) => entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add("is-visible");
        observer.unobserve(entry.target);
      }
    }), { threshold: 0.16 });
    sections.forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, []);

  return <div className="home-page">
    <section className="home-hero" data-scroll-reveal>
      <div className="home-hero-copy">
        <span className="eyebrow">CAREER ACTION PLATFORM</span>
        <h1>다음 커리어를 향한<br /><em>가장 현실적인 준비.</em></h1>
        <p>Job-A-Dream과 함께 내 역량을 채용공고 요구사항과 비교하고,<br />지금 할 수 있는 지원부터 다음 성장 행동까지 준비해 보세요.</p>
        <div className="home-hero-actions"><Link to="/dashboard" className="primary-button">맞춤 채용 분석 보기 <ArrowRight size={17} /></Link><Link to="/mock-interview" className="home-secondary-link">AI 모의면접 시작</Link></div>
      </div>
      <aside className="home-hero-summary">
        <span>오늘의 커리어 액션</span>
        <strong>내게 맞는 공고를<br />찾아볼 시간이에요.</strong>
        <div><CheckCircle2 size={17} /><p><b>역량 프로필</b>을 채우면 공고별 준비도가 더 정확해져요.</p></div>
        <Link to="/profile">내 역량 업데이트 <ArrowRight size={15} /></Link>
      </aside>
    </section>

    <section className="home-quick-section" data-scroll-reveal>
      <div className="home-section-heading"><div><span className="eyebrow">START HERE</span><h2>오늘 무엇을 해볼까요?</h2><p className="home-section-description">나에게 맞는 채용공고를 찾아보고, 필요한 준비를 하나씩 시작해 보세요.</p></div><Link to="/dashboard">대시보드 보기 <ArrowRight size={15} /></Link></div>
      <div className="home-quick-grid">{quickLinks.map(({ to, icon: Icon, title, body }) => <Link key={to} to={to} className="home-quick-card"><span><Icon size={20} /></span><div><h3>{title}</h3><p>{body}</p></div><ArrowRight size={17} /></Link>)}</div>
    </section>

    <HomeJobCarousels />
    <section className="home-market-layout">
      <div className="home-stat-card" data-scroll-reveal>
        <div className="home-section-heading"><div><span className="eyebrow">ICT INSIGHTS</span><h2>ICT 취업 준비 인사이트</h2></div><Link to="/statistics">통계 자세히 보기 <ArrowRight size={15} /></Link></div>
        <p className="home-section-description">ICT 채용에서 자주 확인되는 준비 요소를 한눈에 확인하세요.</p>
        <div className="home-insight-bars">{insightBars.map((item) => <div key={item.label}><div><span>{item.label}</span><b>{item.value}%</b></div><i><em style={{ width: `${item.value}%` }} /></i></div>)}</div>
      </div>
      <section className="home-wordcloud-section home-wordcloud-card" data-scroll-reveal>
        <div className="home-section-heading"><div><span className="eyebrow">MARKET INTELLIGENCE</span><h2>채용공고 기술 키워드 트렌드</h2></div><Link to="/skill-relation">크게 보기 <ArrowRight size={15} /></Link></div>
        <p className="home-section-description">채용공고 요구사항에서 자주 언급되는 핵심 기술을 확인하세요.</p>
        <WordCloudSection showHeader={false} compact />
      </section>
    </section>

    <section className="home-growth-card home-growth-banner" data-scroll-reveal><span><Sparkles size={18} /></span><div><strong>추천은 결과가 아니라 다음 행동의 시작이에요.</strong><p>공고별 부족 요건과 보완 행동을 확인하고 성장 기회를 연결해 보세요.</p></div><Link to="/opportunities">성장 기회 추천 보기 <ArrowRight size={15} /></Link></section>

    <section className="home-statistics-section" data-scroll-reveal>
      <IctStatisticsPanel />
    </section>
  </div>;
}

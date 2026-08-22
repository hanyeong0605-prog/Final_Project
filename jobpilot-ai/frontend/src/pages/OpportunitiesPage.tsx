import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useInterests } from "../features/interests/model/InterestContext";
import { getRecommendedOpportunities } from "../features/opportunities/api/opportunitiesApi";
import { getOpportunityInterestIds, toggleOpportunityInterest } from "../features/opportunities/api/opportunityInterestsApi";
import { CertificateOpportunitySection } from "../features/opportunities/components/CertificateOpportunitySection";
import { OpportunityCard } from "../features/opportunities/components/OpportunityCard";
import type { Opportunity } from "../features/opportunities/model/opportunity.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";

export function OpportunitiesPage() {
  const [opportunities, setOpportunities] = useState<Opportunity[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [category, setCategory] = useState<"ALL" | "CERTIFICATE" | "TRAINING" | "OTHER">("ALL");
  const [trainingView, setTrainingView] = useState<"OPEN" | "CLOSED">("OPEN");
  const [searchParams] = useSearchParams();
  const { } = useInterests(); const [interestIds, setInterestIds] = useState<number[]>([]);

  useEffect(() => {
    void getRecommendedOpportunities()
      .then((data) => { setOpportunities(data); setStatus("ready"); })
      .catch(() => { setOpportunities([]); setStatus("error"); }); void getOpportunityInterestIds().then(setInterestIds).catch(() => setInterestIds([]));
  }, []);

  return <>
    <PageHeading eyebrow="GROWTH OPPORTUNITIES" title="부족한 근거를 채울 기회" body="매칭 분석의 부족 요건을 기준으로 자격증·고용24 훈련과정·프로젝트 기회를 연결합니다." />
    {searchParams.get("requirementId") && <div className="opportunity-context">선택한 공고의 부족 요건에 맞춘 성장 기회를 보고 있습니다.</div>}
    <nav className="opportunity-category-tabs" aria-label="성장 기회 카테고리"><button className={category === "ALL" ? "active" : ""} onClick={() => setCategory("ALL")}>전체</button><button className={category === "CERTIFICATE" ? "active" : ""} onClick={() => setCategory("CERTIFICATE")}>자격증 정보</button><button className={category === "TRAINING" ? "active" : ""} onClick={() => setCategory("TRAINING")}>고용24 훈련과정</button><button className={category === "OTHER" ? "active" : ""} onClick={() => setCategory("OTHER")}>기타 기회</button></nav>
    {(category === "ALL" || category === "CERTIFICATE") && <CertificateOpportunitySection />}
    {(category === "ALL" || category === "TRAINING" || category === "OTHER") && <><div className="opportunity-section-heading"><h2 className="opportunity-section-title">{category === "TRAINING" ? "고용24 훈련과정" : "교육·공모전·청년지원"}</h2>{(category === "ALL" || category === "TRAINING") && <div className="training-status-tabs" aria-label="훈련과정 상태"><button className={trainingView === "OPEN" ? "active" : ""} onClick={() => setTrainingView("OPEN")}>모집 예정</button><button className={trainingView === "CLOSED" ? "active" : ""} onClick={() => setTrainingView("CLOSED")}>진행·종료 과정</button></div>}</div>
    {status === "loading" && <DataStatePanel state="loading" />}
    {status === "error" && <DataStatePanel state="error" />}
    {status === "ready" && opportunities.length === 0 && <DataStatePanel state="empty" emptyTitle="추천할 기회 정보가 없습니다" emptyBody="회원의 부족 역량과 연결된 실제 기회 데이터가 생기면 표시됩니다." />}
    {status === "ready" && opportunities.length > 0 && <section className="opportunity-grid">{opportunities.filter((item) => category !== "TRAINING" || item.type === "교육").filter((item) => category !== "OTHER" || item.type !== "자격증").filter((item) => item.type !== "교육" || (trainingView === "CLOSED" ? item.status === "IN_PROGRESS" || item.status === "EXPIRED" : item.status === "ACTIVE")).sort((a, b) => (a.type === "교육" && b.type === "교육" ? (a.startAt || "9999-12-31").localeCompare(b.startAt || "9999-12-31") : 0)).map((item) => <OpportunityCard key={item.id} item={item} interested={interestIds.includes(item.id)} onInterest={() => { const next=!interestIds.includes(item.id); setInterestIds(next ? [...interestIds,item.id] : interestIds.filter((id) => id !== item.id)); void toggleOpportunityInterest(item.id,next).catch(() => setInterestIds(interestIds)); }} />)}</section>}</>}
  </>;
}

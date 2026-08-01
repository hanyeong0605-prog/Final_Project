import { useEffect, useState } from "react";
import { useInterests } from "../features/interests/model/InterestContext";
import { getRecommendedOpportunities } from "../features/opportunities/api/opportunitiesApi";
import { OpportunityCard } from "../features/opportunities/components/OpportunityCard";
import type { Opportunity } from "../features/opportunities/model/opportunity.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";

export function OpportunitiesPage() {
  const [opportunities, setOpportunities] = useState<Opportunity[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const { isInterested, toggleInterest } = useInterests();

  useEffect(() => {
    void getRecommendedOpportunities()
      .then((data) => { setOpportunities(data); setStatus("ready"); })
      .catch(() => { setOpportunities([]); setStatus("error"); });
  }, []);

  return <>
    <PageHeading eyebrow="GROWTH OPPORTUNITIES" title="부족한 근거를 채울 기회" body="매칭 분석에서 발견한 보완 항목을 기준으로 교육·자격증·공모전·청년지원을 연결합니다." />
    {status === "loading" && <DataStatePanel state="loading" />}
    {status === "error" && <DataStatePanel state="error" />}
    {status === "ready" && opportunities.length === 0 && <DataStatePanel state="empty" emptyTitle="추천할 기회 정보가 없습니다" emptyBody="회원의 부족 역량과 연결된 실제 기회 데이터가 생기면 표시됩니다." />}
    {status === "ready" && opportunities.length > 0 && <section className="opportunity-grid">{opportunities.map((item) => <OpportunityCard key={item.id} item={item} interested={isInterested(item.id)} onInterest={() => toggleInterest(item.id)} />)}</section>}
  </>;
}

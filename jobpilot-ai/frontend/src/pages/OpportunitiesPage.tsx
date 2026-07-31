import { useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import { useInterests } from "../features/interests/model/InterestContext";
import { getRecommendedOpportunities } from "../features/opportunities/api/opportunitiesApi";
import { OpportunityCard } from "../features/opportunities/components/OpportunityCard";
import type { Opportunity } from "../features/opportunities/model/opportunity.types";
import { PageHeading } from "../shared/components/PageHeading";

export function OpportunitiesPage() {
  const [opportunities, setOpportunities] = useState<Opportunity[]>([]);
  const { isInterested, toggleInterest } = useInterests();
  useEffect(() => { void getRecommendedOpportunities().then(setOpportunities); }, []);

  return <><PageHeading eyebrow="GROWTH OPPORTUNITIES" title="부족한 근거를 채울 기회" body="공고에서 발견된 보완 항목을 기준으로 교육·자격증·공모전·청년지원을 연결합니다." /><section className="insight-card"><div className="insight-icon"><Sparkles size={23} /></div><div><span>추천 기준</span><h2>Docker·AWS 배포 경험과 SQL 역량을 보완하면<br />‘보완 후 도전 가능’ 공고의 지원 근거가 강화됩니다.</h2></div><div className="insight-tags"><span>Docker</span><span>AWS</span><span>SQL</span></div></section><div className="opportunity-tabs"><button className="active">전체 {opportunities.length}</button><button>교육</button><button>자격증</button><button>공모전</button><button>청년지원</button></div><section className="opportunity-grid">{opportunities.map((item) => <OpportunityCard key={item.id} item={item} interested={isInterested(item.id)} onInterest={() => toggleInterest(item.id)} />)}</section></>;
}

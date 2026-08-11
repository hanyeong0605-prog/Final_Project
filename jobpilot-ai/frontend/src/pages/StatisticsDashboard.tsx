import { IctStatisticsPanel } from "../features/statistics/components/IctStatisticsPanel";
import { PageHeading } from "../shared/components/PageHeading";

export function StatisticsDashboard() {
  return <>
    <PageHeading
      eyebrow="ICT STATISTICS"
      title="ICT 전문인력 통계 대시보드"
      body="구직 시 겪는 어려움과 취업에 도움이 된 주요 요인 데이터를 시각화하여 제공합니다."
    />
    <IctStatisticsPanel showTable />
  </>;
}

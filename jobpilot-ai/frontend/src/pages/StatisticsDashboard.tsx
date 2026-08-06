import { useState } from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js';
import { Bar } from 'react-chartjs-2';

import jobDiffData from '../data/jobdiff.json';
import helpfulData from '../data/helpful.json';

import { BarChart3 } from "lucide-react";
import { PageHeading } from "../shared/components/PageHeading";
import { PanelTitle } from "../shared/components/PanelTitle";

// Chart.js 모듈 등록
ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend);

type TabType = 'difficulty' | 'helpFactor';

export function StatisticsDashboard() {
  const [activeTab, setActiveTab] = useState<TabType>('difficulty');

  const targetJobDiff = (jobDiffData as any[]).find(
    (item) => item.educationLevel === "00 전체" && item.majorType === "C01 계"
  );
  const targetHelpful = (helpfulData as any[]).find(
    (item) => item.educationLevel === "00 전체" && item.majorType === "C01 계"
  );

  const jobDiffLabels = targetJobDiff ? Object.keys(targetJobDiff.values).map(k => k.replace(/^\d+\s*/, '')) : [];
  const jobDiffValues = targetJobDiff ? Object.values(targetJobDiff.values).map(v => Number(v)) : [];

  const helpfulLabels = targetHelpful ? Object.keys(targetHelpful.values).map(k => k.replace(/^\d+\s*/, '')) : [];
  const helpfulValues = targetHelpful ? Object.values(targetHelpful.values).map(v => Number(v)) : [];

  const currentData = {
    title: activeTab === 'difficulty' ? "구직할 때 어려움을 겪는 이유" : "ICT관련 직종 취업에 가장 큰 도움이 된 사항",
    labels: activeTab === 'difficulty' ? jobDiffLabels : helpfulLabels,
    values: activeTab === 'difficulty' ? jobDiffValues : helpfulValues,
  };

  const chartData = {
    labels: currentData.labels,
    datasets: [
      {
        label: '비중 (%)',
        data: currentData.values,
        backgroundColor: activeTab === 'difficulty' ? 'rgba(236, 72, 153, 0.6)' : 'rgba(54, 162, 235, 0.6)',
        borderColor: activeTab === 'difficulty' ? 'rgba(236, 72, 153, 1)' : 'rgba(54, 162, 235, 1)',
        borderWidth: 1,
      },
    ],
  };

  const chartOptions = {
    responsive: true,
    plugins: {
      legend: { position: 'top' as const },
      title: { display: true, text: currentData.title },
    },
  };

  return (
    <>
      <PageHeading 
        eyebrow="ICT STATISTICS" 
        title="ICT 전문인력 통계 대시보드" 
        body="구직 시 겪는 어려움과 취업에 도움이 된 주요 요인 데이터를 시각화하여 제공합니다." 
      />

      {/* 탭 버튼 영역 */}
      <div style={{ display: 'flex', gap: '10px', marginBottom: '20px' }}>
        <button
          onClick={() => setActiveTab('difficulty')}
          className={activeTab === 'difficulty' ? "primary-button" : "outline-button"}
          style={{ cursor: 'pointer' }}
        >
          구직 시 어려움
        </button>
        <button
          onClick={() => setActiveTab('helpFactor')}
          className={activeTab === 'helpFactor' ? "primary-button" : "outline-button"}
          style={{ cursor: 'pointer' }}
        >
          취업에 도움된 사항
        </button>
      </div>

      {/* 차트 영역 */}
      <section className="panel" style={{ marginBottom: '24px', padding: '20px' }}>
        <PanelTitle title={currentData.title} subtitle="항목별 응답 비중 통계 결과입니다." />
        <div style={{ background: '#fff', padding: '10px', borderRadius: '8px' }}>
          <Bar data={chartData} options={chartOptions} />
        </div>
      </section>

      {/* 상세 데이터 표 영역 */}
      <section className="panel" style={{ padding: '20px' }}>
        <PanelTitle title="상세 수치표" subtitle="데이터 항목별 구체적인 비율 수치입니다." />
        <div style={{ overflowX: 'auto', marginTop: '15px' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
            <thead>
              <tr style={{ background: '#f1f3f5' }}>
                <th style={{ padding: '12px', borderBottom: '1px solid #dee2e6' }}>항목</th>
                <th style={{ padding: '12px', borderBottom: '1px solid #dee2e6', width: '120px' }}>비율 (%)</th>
              </tr>
            </thead>
            <tbody>
              {currentData.labels.map((label: string, index: number) => (
                <tr key={index}>
                  <td style={{ padding: '12px', borderBottom: '1px solid #dee2e6' }}>{label}</td>
                  <td style={{ padding: '12px', borderBottom: '1px solid #dee2e6' }}>{currentData.values[index]}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
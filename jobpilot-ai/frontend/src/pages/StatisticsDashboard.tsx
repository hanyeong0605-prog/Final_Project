/* npm install chart.js react-chartjs-2 react에서 차트그리는 용도 설치 */
import React, { useState } from 'react';
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

// Chart.js 모듈 등록
ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend);

// 데이터 구조 타입 정의
interface StatItem {
  title: string;
  labels: string[];
  values: number[];
}

type TabType = 'difficulty' | 'helpFactor';

// 통계 데이터 객체
const statisticsData: Record<TabType, StatItem> = {
  difficulty: {
    title: "구직할 때 어려움을 겪는 이유",
    labels: ["전공/경력에 맞는 일자리 부족", "임금/근로조건 불일치", "교육·기술·경험 부족", "주변에 일거리 부족"],
    values: [33.0, 20.5, 20.2, 22.6],
  },
  helpFactor: {
    title: "ICT관련 직종 취업에 가장 큰 도움이 된 사항",
    labels: ["대학(원) 강의 수강", "캡스톤 R&D 등 비교과", "자격증 취득", "공공훈련", "민간기관 훈련"],
    values: [41.2, 28.0, 6.8, 8.6, 9.9],
  },
};

export function StatisticsDashboard() {
  // 현재 선택된 탭 상태 타입 지정
  const [activeTab, setActiveTab] = useState<TabType>('difficulty');

  // 현재 탭에 맞는 데이터 선택
  const currentData = statisticsData[activeTab];

  // 차트 데이터 설정
  const chartData = {
    labels: currentData.labels,
    datasets: [
      {
        label: '비중 (%)',
        data: currentData.values,
        backgroundColor: 'rgba(54, 162, 235, 0.6)',
        borderColor: 'rgba(54, 162, 235, 1)',
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
    <div style={{ maxWidth: '800px', margin: '0 auto', padding: '20px' }}>
      <h2>📊 ICT 전문인력 통계 대시보드</h2>

      {/* 1. 상단 탭 버튼 영역 */}
      <div style={{ display: 'flex', gap: '10px', marginBottom: '20px' }}>
        <button
          onClick={() => setActiveTab('difficulty')}
          style={{
            padding: '10px 15px',
            background: activeTab === 'difficulty' ? '#007bff' : '#f8f9fa',
            color: activeTab === 'difficulty' ? '#fff' : '#000',
            border: '1px solid #ccc',
            cursor: 'pointer',
            borderRadius: '4px',
          }}
        >
          구직 시 어려움
        </button>
        <button
          onClick={() => setActiveTab('helpFactor')}
          style={{
            padding: '10px 15px',
            background: activeTab === 'helpFactor' ? '#007bff' : '#f8f9fa',
            color: activeTab === 'helpFactor' ? '#fff' : '#000',
            border: '1px solid #ccc',
            cursor: 'pointer',
            borderRadius: '4px',
          }}
        >
          취업에 도움된 사항
        </button>
      </div>

      {/* 2. 선택된 탭의 내용 (차트 영역) */}
      <div style={{ background: '#fff', padding: '20px', borderRadius: '8px', boxShadow: '0 2px 4px rgba(0,0,0,0.1)' }}>
        <Bar data={chartData} options={chartOptions} />
      </div>

      {/* 3. 상세 데이터 표 영역 */}
      <div style={{ marginTop: '30px' }}>
        <h3>상세 수치표</h3>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
          <thead>
            <tr style={{ background: '#f1f3f5' }}>
              <th style={{ padding: '10px', border: '1px solid #dee2e6' }}>항목</th>
              <th style={{ padding: '10px', border: '1px solid #dee2e6' }}>비율 (%)</th>
            </tr>
          </thead>
          <tbody>
            {currentData.labels.map((label: string, index: number) => (
              <tr key={index}>
                <td style={{ padding: '10px', border: '1px solid #dee2e6' }}>{label}</td>
                <td style={{ padding: '10px', border: '1px solid #dee2e6' }}>{currentData.values[index]}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
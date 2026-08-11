import { useMemo, useState } from "react";
import {
  BarElement,
  CategoryScale,
  Chart as ChartJS,
  Legend,
  LinearScale,
  Title,
  Tooltip,
} from "chart.js";
import { Bar } from "react-chartjs-2";
import helpfulData from "../../../data/helpful.json";
import jobDiffData from "../../../data/jobdiff.json";

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend);

type TabType = "difficulty" | "helpFactor";
type StatisticRow = {
  educationLevel?: string;
  majorType?: string;
  values?: Record<string, number | string>;
};

function findOverallRow(rows: StatisticRow[]) {
  return rows.find((row) => row.educationLevel?.startsWith("00") && row.majorType?.startsWith("C01")) ?? rows[0];
}

function compactChartLabel(label: string): string | string[] {
  const normalized = label.replace(/^\d+\s*/, "");

  if (normalized.includes("전공") && normalized.includes("경력")) return ["전공·경력 맞춤", "일자리 부족"];
  if (normalized.includes("임금") || normalized.includes("근로조건")) return ["임금·근로조건", "맞춤 일자리 부족"];
  if (normalized.includes("교육") || normalized.includes("기술") || normalized.includes("경험")) return "교육·기술·경험 부족";
  if (normalized.includes("주변") || normalized.includes("근처")) return "주변 일자리 부족";
  if (normalized.includes("건강")) return "건강 문제";

  return normalized.length > 14
    ? [normalized.slice(0, 13), normalized.slice(13)]
    : normalized;
}

function toSeries(row?: StatisticRow) {
  const values = row?.values ?? {};
  const labels = Object.keys(values).map((label) => label.replace(/^\d+\s*/, ""));
  return {
    labels,
    chartLabels: labels.map(compactChartLabel),
    values: Object.values(values).map((value) => Number(value)),
  };
}

export function IctStatisticsPanel({ showTable = false }: { showTable?: boolean }) {
  const [activeTab, setActiveTab] = useState<TabType>("difficulty");
  const difficulty = useMemo(() => toSeries(findOverallRow(jobDiffData as StatisticRow[])), []);
  const helpFactor = useMemo(() => toSeries(findOverallRow(helpfulData as StatisticRow[])), []);
  const current = activeTab === "difficulty" ? difficulty : helpFactor;
  const title = activeTab === "difficulty" ? "구직할 때 어려움을 겪는 이유" : "ICT 직종 취업에 가장 도움이 된 사항";
  const color = activeTab === "difficulty"
    ? { fill: "rgba(96, 112, 239, .58)", border: "#5c70e8" }
    : { fill: "rgba(154, 126, 231, .54)", border: "#9170da" };

  return <section className="ict-statistics-panel">
    <div className="ict-statistics-panel__top">
      <div>
        <span className="eyebrow">ICT DATA</span>
        <h2>{title}</h2>
        <p>ICT 전문인력 조사 기반의 항목별 응답 비중입니다.</p>
      </div>
      <div className="ict-statistics-tabs" role="tablist" aria-label="ICT 통계 선택">
        <button type="button" onClick={() => setActiveTab("difficulty")} className={activeTab === "difficulty" ? "active" : ""}>구직 시 어려움</button>
        <button type="button" onClick={() => setActiveTab("helpFactor")} className={activeTab === "helpFactor" ? "active" : ""}>취업 도움 요인</button>
      </div>
    </div>
    <div className="ict-statistics-chart">
      <Bar
        data={{ labels: current.chartLabels, datasets: [{ label: "응답 비중 (%)", data: current.values, backgroundColor: color.fill, borderColor: color.border, borderWidth: 0, borderRadius: 7, borderSkipped: false }] }}
        options={{ responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { backgroundColor: "#283d70", padding: 10, displayColors: false, callbacks: { title: (items) => current.labels[items[0].dataIndex] } } }, scales: { x: { grid: { display: false }, ticks: { autoSkip: false, maxRotation: 0, minRotation: 0, color: "#71809a", font: { size: 11, weight: 600 } } }, y: { beginAtZero: true, grid: { color: "#edf0f7" }, ticks: { color: "#94a0b5", callback: (value) => `${value}%`, font: { size: 10 } } } } }}
      />
    </div>
    <div className="ict-statistics-message">
      <span>JOB-A-DREAM</span>
      <p><b>막막한 취업 준비, 다음 행동으로.</b><small>내 역량과 공고 요건을 비교해 필요한 준비를 바로 알려드립니다.</small></p>
    </div>
    {showTable && <div className="ict-statistics-table-wrap"><table><thead><tr><th>항목</th><th>비율</th></tr></thead><tbody>{current.labels.map((label, index) => <tr key={label}><td>{label}</td><td>{current.values[index]}%</td></tr>)}</tbody></table></div>}
  </section>;
}

import { useEffect, useMemo, useState } from "react";
import { BarChart3, CircleAlert, ExternalLink, Landmark, ShieldCheck, Sparkles, TrendingUp } from "lucide-react";
import { Bar } from "react-chartjs-2";
import {
  BarElement, CategoryScale, Chart as ChartJS, Legend, LinearScale, Tooltip,
} from "chart.js";
import { getCompanyFinance } from "../api/companyFinanceApi";
import type { CompanyFinanceAnalysis, CompanyFinancialYear } from "../model/companyFinance.types";

ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip, Legend);

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: false }, tooltip: { callbacks: { label: (context: { raw: unknown }) => money(Number(context.raw)) } } },
  scales: {
    x: { grid: { display: false }, ticks: { color: "#8290a5", font: { size: 10 } } },
    y: { grid: { color: "#edf1f7" }, ticks: { color: "#8290a5", font: { size: 10 }, callback: (value: string | number) => shortMoney(Number(value)) } },
  },
} as const;

function money(value: number | null) {
  if (value == null || !Number.isFinite(value)) return "자료 없음";
  const absolute = Math.abs(value);
  const sign = value < 0 ? "-" : "";
  if (absolute >= 1_0000_0000_0000) return `${sign}${(absolute / 1_0000_0000_0000).toFixed(1)}조원`;
  if (absolute >= 1_0000_0000) return `${sign}${(absolute / 1_0000_0000).toFixed(1)}억원`;
  return `${value.toLocaleString("ko-KR")}원`;
}

function shortMoney(value: number) {
  const absolute = Math.abs(value);
  const sign = value < 0 ? "-" : "";
  if (absolute >= 1_0000_0000_0000) return `${sign}${(absolute / 1_0000_0000_0000).toFixed(0)}조`;
  if (absolute >= 1_0000_0000) return `${sign}${(absolute / 1_0000_0000).toFixed(0)}억`;
  return `${sign}${(absolute / 1_0000).toFixed(0)}만`;
}

function ratio(numerator: number | null, denominator: number | null) {
  if (numerator == null || denominator == null || denominator === 0) return null;
  return (numerator / denominator) * 100;
}

function receiptUrl(receiptNumber: string) {
  return `https://dart.fss.or.kr/dsaf001/main.do?rcpNo=${encodeURIComponent(receiptNumber)}`;
}

function outlookLabel(value: string) {
  const labels: Record<string, string> = { POSITIVE: "긍정적 ↑", CAUTION: "주의 필요", NEGATIVE: "부정적 ↓", NEUTRAL: "중립" };
  return labels[value.toUpperCase()] ?? value;
}

function confidenceLabel(value: string) {
  const labels: Record<string, string> = { HIGH: "높음", MEDIUM: "보통", LOW: "낮음" };
  return labels[value.toUpperCase()] ?? value;
}

function FinanceChart({ title, rows, field, tone = "blue" }: {
  title: string;
  rows: CompanyFinancialYear[];
  field: "revenue" | "operatingIncome" | "netIncome";
  tone?: "blue" | "green" | "purple";
}) {
  const colors = { blue: ["#6d85f5", "#edf1ff"], green: ["#58b995", "#e8f7f1"], purple: ["#9477dc", "#f2edff"] };
  const values = rows.map((row) => row[field]);
  const latest = [...values].reverse().find((value) => value != null) ?? null;
  const [line, fill] = colors[tone];
  return <article className="company-finance-chart-card">
    <header><span>{title}</span><strong>{money(latest)}</strong></header>
    <div className="company-finance-chart"><Bar data={{ labels: rows.map((row) => `${row.businessYear}`), datasets: [{ data: values, backgroundColor: values.map((value) => value != null && value < 0 ? "#ef8d82" : line), borderColor: line, borderRadius: 5 }] }} options={chartOptions} /></div>
    <i style={{ background: fill }} />
  </article>;
}

function ForecastCard({ data }: { data: CompanyFinanceAnalysis }) {
  const forecast = data.forecast;
  if (!forecast || data.status !== "READY") return <article className="company-finance-forecast pending">
    <span className="company-finance-icon"><ShieldCheck size={20} /></span>
    <div><span className="eyebrow">MODEL STATUS</span><h3>검증된 성장 전망 준비 중</h3><p>현재는 DART 재무 사실만 제공합니다. 평가 기준을 통과한 저장 모델 결과가 있을 때만 전망을 표시합니다.</p></div>
  </article>;
  const outlookClass = forecast.outlook.toLowerCase();
  return <article className={`company-finance-forecast ${outlookClass}`}>
    <span className="company-finance-icon"><Sparkles size={20} /></span>
    <div className="company-finance-forecast-copy"><span className="eyebrow">VERIFIED ML OUTLOOK · {forecast.modelVersion}</span><h3>다음 사업연도 성장 가능성: {outlookLabel(forecast.outlook)}</h3><p>{forecast.baseYear}년까지 공개된 데이터로 계산한 저장 결과이며 신뢰도는 {confidenceLabel(forecast.confidence)}입니다.</p><ul>{forecast.evidence.slice(0, 3).map((item) => <li key={item}>{item}</li>)}</ul></div>
    <div className="company-finance-probabilities"><span><b>{Math.round(forecast.growthProbability * 100)}%</b>매출 성장</span><span><b>{Math.round(forecast.profitabilityImprovementProbability * 100)}%</b>수익성 개선</span><span><b>{forecast.stabilityRiskProbability >= .5 ? "주의" : "양호"}</b>재무 위험 신호</span></div>
  </article>;
}

export function CompanyFinanceSection({ postingId }: { postingId: number }) {
  const [data, setData] = useState<CompanyFinanceAnalysis | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setFailed(false);
    getCompanyFinance(postingId, controller.signal).then(setData).catch((error) => {
      if ((error as Error).name !== "AbortError") setFailed(true);
    });
    return () => controller.abort();
  }, [postingId]);

  const rows = useMemo(() => data?.financials.slice(-5) ?? [], [data]);
  const latest = rows.at(-1);
  const debtRatio = latest ? ratio(latest.totalLiabilities, latest.totalEquity) : null;
  const cashFlowRatio = latest ? ratio(latest.operatingCashFlow, latest.revenue) : null;
  const unavailable = failed || (data && ["UNMATCHED", "FINANCIALS_NOT_FOUND", "TEMPORARILY_UNAVAILABLE"].includes(data.status));

  return <section id="company-finance" className="company-finance-section" aria-labelledby="company-finance-title">
    <div className="company-finance-heading"><div><span className="eyebrow">COMPANY FINANCE</span><h2 id="company-finance-title">기업 재무 분석</h2><p>DART에 정확히 연결된 공시법인의 저장된 재무제표만 보여드립니다.</p></div><Landmark size={25} /></div>
    {!data && !failed && <div className="company-finance-state"><BarChart3 className="spinning" size={22} /><strong>재무정보를 불러오는 중입니다.</strong></div>}
    {unavailable && <div className="company-finance-state"><CircleAlert size={22} /><div><strong>재무 분석을 표시할 수 없습니다.</strong><p>{failed ? "재무정보를 일시적으로 불러오지 못했습니다. 잠시 후 다시 시도해 주세요." : data?.message}</p></div></div>}
    {data && rows.length > 0 && <>
      <ForecastCard data={data} />
      {data.status === "DATA_INSUFFICIENT" && <div className="company-finance-inline-notice"><CircleAlert size={16} />{data.message}</div>}
      <div className="company-finance-chart-grid">
        <FinanceChart title="매출액" rows={rows} field="revenue" />
        <FinanceChart title="영업이익" rows={rows} field="operatingIncome" tone="green" />
        <FinanceChart title="당기순이익" rows={rows} field="netIncome" tone="purple" />
      </div>
      <div className="company-finance-health-grid">
        <article><span><TrendingUp size={16} />재무 안정성</span><strong>{debtRatio == null ? "자료 없음" : `부채비율 ${debtRatio.toFixed(1)}%`}</strong><p>최근 사업연도 부채총계를 자본총계로 나눈 값입니다.</p></article>
        <article><span><BarChart3 size={16} />영업 현금흐름</span><strong>{latest?.operatingCashFlow == null ? "자료 없음" : money(latest.operatingCashFlow)}</strong><p>{cashFlowRatio == null ? "매출 대비 비율을 계산할 자료가 부족합니다." : `매출 대비 ${cashFlowRatio.toFixed(1)}% 수준입니다.`}</p></article>
      </div>
      <footer className="company-finance-source"><div><ShieldCheck size={15} /><span>출처: 금융감독원 전자공시시스템 DART · {latest?.fsDiv === "CFS" ? "연결" : "별도"}재무제표 · 최근 {rows.length}개 사업연도</span></div><div>{rows.filter((row) => row.receiptNumber).map((row) => <a key={`${row.businessYear}-${row.receiptNumber}`} href={receiptUrl(row.receiptNumber!)} target="_blank" rel="noreferrer">{row.businessYear} 공시 <ExternalLink size={11} /></a>)}</div><small>재무 추이와 모델 전망은 투자 조언이나 미래 실적 보장이 아닙니다.</small></footer>
    </>}
  </section>;
}

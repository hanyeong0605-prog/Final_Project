// 2026-08-12 추가: audio_analysis.py가 시계열(다운샘플링된 60포인트)로 반환하는 피치/음량을
// 답변 리포트에 선 그래프로 보여준다 - 지금까지는 평균값 카드(metricLabels)만 있었는데,
// "평균 음높이 165Hz"보다 "이 구간에서 톤이 이렇게 오르내렸다"는 변화 추이가 훨씬 직관적이다.
import { useMemo } from "react";
import {
  CategoryScale,
  Chart as ChartJS,
  Legend,
  LinearScale,
  LineElement,
  PointElement,
  Title,
  Tooltip,
} from "chart.js";
import { Line } from "react-chartjs-2";
import type { VoiceMetrics } from "../model/mockInterview.types";

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend);

export function VoiceTimelineChart({ metrics }: { metrics: VoiceMetrics }) {
  const { timeline_seconds, timeline_pitch_hz, timeline_volume_rms } = metrics;
  const hasTimeline = Boolean(timeline_seconds && timeline_seconds.length > 1);

  const data = useMemo(
    () => ({
      labels: (timeline_seconds ?? []).map((t) => `${t.toFixed(0)}s`),
      datasets: [
        {
          label: "음높이 (Hz)",
          data: timeline_pitch_hz ?? [],
          borderColor: "#596ff3",
          backgroundColor: "rgba(89, 111, 243, 0.12)",
          yAxisID: "yPitch",
          // null(무성음/침묵 구간)은 선을 억지로 이어붙이지 않고 끊어서 보여준다 - 실제로
          // 측정 안 된 구간을 있는 것처럼 표시하지 않기 위함(감정/긴장도 추정 금지 원칙과 같은 맥락).
          spanGaps: false,
          pointRadius: 0,
          borderWidth: 2,
          tension: 0.3,
        },
        {
          label: "음량 (RMS)",
          data: timeline_volume_rms ?? [],
          borderColor: "#f2a33c",
          backgroundColor: "rgba(242, 163, 60, 0.12)",
          yAxisID: "yVolume",
          pointRadius: 0,
          borderWidth: 2,
          tension: 0.3,
        },
      ],
    }),
    [timeline_seconds, timeline_pitch_hz, timeline_volume_rms],
  );

  if (!hasTimeline) return null;

  return (
    <div style={{ marginTop: 16, padding: 16, borderRadius: 12, background: "#fff", border: "1px solid #eef0f6" }}>
      <span className="interview-field-label">답변 중 음높이/음량 변화</span>
      <div style={{ height: 180, marginTop: 8 }}>
        <Line
          data={data}
          options={{
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: "index", intersect: false },
            plugins: {
              legend: { position: "top", labels: { boxWidth: 10, font: { size: 11 } } },
              tooltip: { titleFont: { size: 11 }, bodyFont: { size: 11 } },
            },
            scales: {
              x: {
                type: "category",
                ticks: { maxTicksLimit: 8, font: { size: 10 } },
                grid: { display: false },
              },
              yPitch: {
                type: "linear",
                position: "left",
                title: { display: true, text: "Hz", font: { size: 10 } },
                ticks: { font: { size: 10 } },
              },
              yVolume: {
                type: "linear",
                position: "right",
                title: { display: true, text: "RMS", font: { size: 10 } },
                ticks: { font: { size: 10 } },
                grid: { drawOnChartArea: false },
              },
            },
          }}
        />
      </div>
      <small style={{ color: "#9098a7", fontSize: 11, display: "block", marginTop: 6 }}>
        음높이 선이 끊긴 구간은 무성음/침묵으로 음높이가 감지되지 않은 구간이에요.
      </small>
    </div>
  );
}

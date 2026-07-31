import type { ReactNode } from "react";

interface MetricCardProps {
  icon: ReactNode;
  label: string;
  value: string;
  hint: string;
  tone: "blue" | "orange" | "purple" | "green";
}

export function MetricCard({ icon, label, value, hint, tone }: MetricCardProps) {
  return <article className={`metric-card ${tone}`}><span className="metric-icon">{icon}</span><div><span>{label}</span><strong>{value}</strong><small>{hint}</small></div></article>;
}

import type { ReactNode } from "react";

interface MetricCardProps {
  icon: ReactNode;
  label: string;
  value: string;
  hint: string;
  tone: "blue" | "orange" | "purple" | "green";
  onClick?: () => void;
}

export function MetricCard({ icon, label, value, hint, tone, onClick }: MetricCardProps) {
  const content = <><span className="metric-icon">{icon}</span><div><span>{label}</span><strong>{value}</strong><small>{hint}</small></div></>;
  return onClick
    ? <button type="button" className={`metric-card ${tone} metric-card-button`} onClick={onClick}>{content}</button>
    : <article className={`metric-card ${tone}`}>{content}</article>;
}

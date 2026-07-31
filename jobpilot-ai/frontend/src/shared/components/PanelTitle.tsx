import type { ReactNode } from "react";

interface PanelTitleProps {
  title: string;
  subtitle?: string;
  action?: ReactNode;
}

export function PanelTitle({ title, subtitle, action }: PanelTitleProps) {
  return <div className="panel-title"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>{action}</div>;
}

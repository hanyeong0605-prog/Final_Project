import type { ReactNode } from "react";

interface PageHeadingProps {
  eyebrow: string;
  title: string;
  body: string;
  action?: ReactNode;
}

export function PageHeading({ eyebrow, title, body, action }: PageHeadingProps) {
  return <div className="page-heading"><div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>{body}</p></div>{action}</div>;
}

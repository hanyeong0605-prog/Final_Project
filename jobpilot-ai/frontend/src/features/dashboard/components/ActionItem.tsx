import { ChevronRight } from "lucide-react";

interface ActionItemProps { number: string; title: string; body: string; tag: string; }

export function ActionItem({ number, title, body, tag }: ActionItemProps) {
  return <article className="action-item"><span>{number}</span><div><strong>{title}</strong><p>{body}</p><em>{tag}</em></div><ChevronRight size={18} /></article>;
}

import { ChevronRight } from "lucide-react";
import type { PlannerEvent } from "../model/planner.types";

export function PlannerEventItem({ event }: { event: PlannerEvent }) {
  return <article className={`planner-event ${event.tone}`}><span>{event.time}</span><div><strong>{event.title}</strong><p>{event.body}</p></div><ChevronRight size={18} /></article>;
}

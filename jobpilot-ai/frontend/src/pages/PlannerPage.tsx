import { FormEvent, useEffect, useState } from "react";
import { ChevronLeft, ChevronRight, ExternalLink, Pencil, Plus, Trash2 } from "lucide-react";
import { createPlannerEvent, deletePlannerEvent, getPlannerEvents, updatePlannerEvent } from "../features/planner/api/plannerApi";
import type { PlannerEvent, PlannerEventInput } from "../features/planner/model/planner.types";
import { DataStatePanel } from "../shared/components/DataStatePanel";
import { PageHeading } from "../shared/components/PageHeading";

const emptyForm = (): PlannerEventInput => ({ eventType: "PERSONAL", title: "", startsAt: "", endsAt: null, allDay: false });
const localValue = (value: string | null) => value ? value.slice(0, 16) : "";
const dateKey = (date: Date) => `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
const isRecruiting = (event: PlannerEvent) => event.sourceType === "JOB_POSTING" || event.eventType === "APPLICATION_PERIOD";
const sourceHref = (event: PlannerEvent) => {
  if (event.sourceType === "JOB_POSTING" && event.sourceId) return `/job-postings/${event.sourceId}`;
  if (event.sourceType === "CERTIFICATE") return "/profile";
  return null;
};

export function PlannerPage() {
  const [events, setEvents] = useState<PlannerEvent[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<PlannerEventInput>(emptyForm());
  const [error, setError] = useState("");
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const [hoveredDate, setHoveredDate] = useState<string | null>(null);
  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const load = () => getPlannerEvents(month).then((data) => { setEvents(data); setStatus("ready"); }).catch(() => { setEvents([]); setStatus("error"); });
  useEffect(() => { setStatus("loading"); setSelectedEventId(null); void load(); }, [month]);
  const monthStart = new Date(month.getFullYear(), month.getMonth(), 1); const gridStart = new Date(monthStart); gridStart.setDate(1 - monthStart.getDay());
  const days = Array.from({ length: 42 }, (_, index) => { const value = new Date(gridStart); value.setDate(gridStart.getDate() + index); return value; });
  const dayEvents = (day: Date) => events.filter((event) => { const start = new Date(event.startsAt); const end = new Date(event.endsAt ?? event.startsAt); const endOfDay = new Date(day); endOfDay.setHours(23, 59, 59, 999); const startOfDay = new Date(day); startOfDay.setHours(0, 0, 0, 0); return start <= endOfDay && end >= startOfDay; });
  const sameDay = (left: Date, right: Date) => left.getFullYear() === right.getFullYear() && left.getMonth() === right.getMonth() && left.getDate() === right.getDate();
  const rangePart = (event: PlannerEvent, day: Date) => { const start = new Date(event.startsAt); const end = new Date(event.endsAt ?? event.startsAt); const starts = sameDay(start, day); const ends = sameDay(end, day); if (starts && ends) return { className: "single", label: event.title }; const edge = `${day.getDay() === 0 ? " week-start" : ""}${day.getDay() === 6 ? " week-end" : ""}`; if (starts) return { className: `range-start${edge}`, label: `${event.title} 시작` }; if (ends) return { className: `range-end${edge}`, label: `${event.title} 마감` }; return { className: `range-middle${edge}`, label: event.title }; };
  const openNew = () => { setEditingId(null); setForm(emptyForm()); setError(""); setFormOpen(true); };
  const openEdit = (event: PlannerEvent) => { setEditingId(event.id); setForm({ eventType: event.eventType, title: event.title, startsAt: localValue(event.startsAt), endsAt: localValue(event.endsAt) || null, allDay: event.allDay }); setError(""); setFormOpen(true); };
  const submit = async (e: FormEvent) => { e.preventDefault(); setError(""); try { const input = { ...form, endsAt: form.endsAt || null }; if (editingId) await updatePlannerEvent(editingId, input); else await createPlannerEvent(input); setFormOpen(false); await load(); } catch (reason) { setError(reason instanceof Error ? reason.message : "일정 저장에 실패했습니다."); } };
  const remove = async (id: number) => { if (!confirm("이 일정을 삭제할까요?")) return; try { await deletePlannerEvent(id); await load(); } catch (reason) { setError(reason instanceof Error ? reason.message : "일정 삭제에 실패했습니다."); } };
  const renderEvent = (event: PlannerEvent) => <article key={event.id} className={`planner-event ${event.tone}${selectedEventId === event.id ? " selected" : ""}`} onClick={() => setSelectedEventId(event.id)}><span>{event.time}</span><div><small className="planner-event-kind">{isRecruiting(event) ? "채용일정" : event.sourceType === "CERTIFICATE" ? "자격증 일정" : "개인 일정"}</small><strong>{event.title}</strong><p>{isRecruiting(event) ? "찜한 공고의 지원 기간입니다." : event.body}</p></div>{event.editable ? <div className="event-actions"><button title="수정" onClick={(e) => { e.stopPropagation(); openEdit(event); }}><Pencil size={15} /></button><button title="삭제" onClick={(e) => { e.stopPropagation(); void remove(event.id); }}><Trash2 size={15} /></button></div> : sourceHref(event) ? <a className="planner-detail-link" href={sourceHref(event) ?? undefined} onClick={(e) => e.stopPropagation()}>상세 보기<ExternalLink size={13} /></a> : <small>자동 일정</small>}</article>;
  const recruitmentEvents = events.filter(isRecruiting); const otherEvents = events.filter((event) => !isRecruiting(event));

  return <><PageHeading eyebrow="MY ACTION PLANNER" title="나의 실행 일정을 관리하세요." body="찜한 채용공고 기간과 자격증·정책·개인 일정을 월별로 확인합니다." action={<button className="outline-button" onClick={openNew}><Plus size={17} />일정 추가</button>} />
    {error && <div className="account-alert error">{error}</div>}
    {formOpen && <section className="panel planner-form"><div className="section-title"><h2>{editingId ? "일정 수정" : "새 일정"}</h2><button onClick={() => setFormOpen(false)}>닫기</button></div><form onSubmit={submit}><label>일정 제목<input required maxLength={500} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></label><label>종류<select value={form.eventType} onChange={(e) => setForm({ ...form, eventType: e.target.value })}><option value="PERSONAL">개인 일정</option><option value="APPLICATION">입사 지원</option><option value="INTERVIEW">면접</option><option value="STUDY">학습</option><option value="CERTIFICATE">자격증</option></select></label><label>시작<input required type="datetime-local" value={form.startsAt} onChange={(e) => setForm({ ...form, startsAt: e.target.value })} /></label><label>종료<input type="datetime-local" value={form.endsAt ?? ""} onChange={(e) => setForm({ ...form, endsAt: e.target.value || null })} /></label><label className="check-label"><input type="checkbox" checked={form.allDay} onChange={(e) => setForm({ ...form, allDay: e.target.checked })} />종일 일정</label><button className="primary-button">저장</button></form></section>}
    {status === "loading" && <DataStatePanel state="loading" />}{status === "error" && <DataStatePanel state="error" />}
    {status === "ready" && <div className="planner-month-layout"><section className="calendar-panel month-calendar"><div className="calendar-head"><button onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}><ChevronLeft /></button><h2>{month.getFullYear()}년 {month.getMonth() + 1}월</h2><button onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}><ChevronRight /></button></div><div className="weekdays">{"일월화수목금토".split("").map((day) => <span key={day}>{day}</span>)}</div><div className="month-grid">{days.map((day) => { const items = dayEvents(day); const outside = day.getMonth() !== month.getMonth(); const expanded = hoveredDate === dateKey(day); const shown = expanded ? items : items.slice(0, 3); return <div key={day.toISOString()} onMouseEnter={() => setHoveredDate(dateKey(day))} onMouseLeave={() => setHoveredDate(null)} className={`${outside ? "month-day outside" : "month-day"}${expanded && items.length > 3 ? " expanded" : ""}`}><strong>{day.getDate()}</strong><div>{shown.map((event) => { const part = rangePart(event, day); return <button key={event.id} className={`calendar-event ${event.tone} ${part.className}${selectedEventId === event.id ? " selected" : ""}`} title={`${isRecruiting(event) ? "채용일정 · " : ""}${event.title} (${event.time})`} onClick={() => setSelectedEventId(event.id)}>{part.label}</button>; })}{!expanded && items.length > 3 && <small className="calendar-more">+{items.length - 3}개 더보기</small>}</div></div>; })}</div><p className="calendar-hover-help">날짜 위에 마우스를 올리면 그날의 모든 일정을 볼 수 있습니다.</p><div className="calendar-legend"><span><i className="blue" />채용일정</span><span><i className="purple" />자격증 일정</span><span><i className="orange" />정책·개인 일정</span></div></section><section className="panel planner-events"><div className="section-title"><h2>{month.getMonth() + 1}월 일정</h2><span>{events.length}개</span></div>{events.length === 0 ? <div className="saved-empty">등록된 일정이 없습니다.</div> : <div className="event-list"><div className="planner-group-title"><span>채용일정</span><b>{recruitmentEvents.length}개</b></div>{recruitmentEvents.length ? recruitmentEvents.map(renderEvent) : <p className="planner-empty-group">찜한 채용공고 일정이 없습니다.</p>}<div className="planner-group-title"><span>기타 일정</span><b>{otherEvents.length}개</b></div>{otherEvents.length ? otherEvents.map(renderEvent) : <p className="planner-empty-group">등록된 기타 일정이 없습니다.</p>}</div>}</section></div>}
  </>;
}

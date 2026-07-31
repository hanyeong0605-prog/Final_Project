import { useEffect, useState } from "react";
import { CalendarDays, Plus } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useInterests } from "../features/interests/model/InterestContext";
import { getPlannerEvents } from "../features/planner/api/plannerApi";
import { PlannerEventItem } from "../features/planner/components/PlannerEventItem";
import type { PlannerEvent } from "../features/planner/model/planner.types";
import { PageHeading } from "../shared/components/PageHeading";
import { PanelTitle } from "../shared/components/PanelTitle";

export function PlannerPage() {
  const [events, setEvents] = useState<PlannerEvent[]>([]);
  const { interestCount } = useInterests();
  const navigate = useNavigate();
  useEffect(() => { void getPlannerEvents().then(setEvents); }, []);

  return <><PageHeading eyebrow="MY ACTION PLANNER" title="관심 등록한 기회를 일정으로 관리하세요." body="채용공고 마감일, 교육 신청·수강 기간, 자격증 시험일, 공모전 접수 기간을 자동으로 연결합니다." action={<button className="outline-button" onClick={() => navigate("/opportunities")}><Plus size={17} />기회 둘러보기</button>} /><section className="planner-layout"><article className="calendar-panel"><div className="calendar-head"><button>‹</button><h2>2026년 8월</h2><button>›</button></div><div className="weekdays">{["일", "월", "화", "수", "목", "금", "토"].map((day) => <span key={day}>{day}</span>)}</div><div className="calendar-grid">{Array.from({ length: 35 }, (_, index) => { const day = index - 5; const inMonth = day > 0 && day <= 31; const hasEvent = [5, 10, 12, 17, 20, 30].includes(day); return <button key={index} className={`${!inMonth ? "outside" : ""} ${day === 5 ? "today" : ""}`}>{inMonth ? day : day <= 0 ? 26 + day : day - 31}{hasEvent && <i />}</button>; })}</div><div className="calendar-legend"><span><i className="dot blue" />채용공고</span><span><i className="dot purple" />자격증</span><span><i className="dot orange" />공모전·교육</span></div></article><article className="planner-events panel"><PanelTitle title="8월 5일 · 수요일" subtitle={`${interestCount}개 관심 기회와 연결된 일정`} /><div className="event-list">{events.map((event) => <PlannerEventItem key={event.id} event={event} />)}</div><button className="full-outline"><CalendarDays size={16} />Google Calendar 연동은 V2 기능</button></article></section></>;
}

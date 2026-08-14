export interface PlannerEvent {
  id: number;
  tone: "blue" | "purple" | "orange";
  time: string;
  title: string;
  body: string;
  eventType: string;
  startsAt: string;
  endsAt: string | null;
  allDay: boolean;
  editable: boolean;
  sourceType: "JOB_POSTING" | "CERTIFICATE" | "MANUAL" | string;
  sourceId: number | null;
}

export interface PlannerEventInput {
  eventType: string;
  title: string;
  startsAt: string;
  endsAt: string | null;
  allDay: boolean;
}

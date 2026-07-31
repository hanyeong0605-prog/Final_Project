export interface PlannerEvent {
  id: number;
  tone: "blue" | "purple" | "orange";
  time: string;
  title: string;
  body: string;
}

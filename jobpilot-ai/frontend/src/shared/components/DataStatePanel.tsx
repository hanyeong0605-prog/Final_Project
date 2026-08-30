import { LoadingScreen } from "./LoadingScreen";

interface DataStatePanelProps {
  state: "loading" | "empty" | "error";
  emptyTitle?: string;
  emptyBody?: string;
}

const content = {
  loading: ["데이터를 불러오는 중입니다", "서버 응답을 기다리고 있습니다."],
  empty: ["아직 저장된 데이터가 없습니다", "데이터가 등록되면 이 영역에 표시됩니다."],
  error: ["서버에 연결할 수 없습니다", "백엔드 서버가 실행 중인지 확인한 뒤 다시 시도해 주세요."],
} as const;

export function DataStatePanel({ state, emptyTitle, emptyBody }: DataStatePanelProps) {
  if (state === "loading") return <section className="data-state-panel loading"><LoadingScreen label="데이터를 불러오는 중입니다" compact /></section>;
  const [defaultTitle, defaultBody] = content[state];
  return (
    <section className={`data-state-panel ${state}`} role={state === "error" ? "alert" : "status"}>
      <span className="data-state-dot" />
      <div>
        <h2>{state === "empty" && emptyTitle ? emptyTitle : defaultTitle}</h2>
        <p>{state === "empty" && emptyBody ? emptyBody : defaultBody}</p>
      </div>
    </section>
  );
}

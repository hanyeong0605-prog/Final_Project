import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from "chart.js";
import { Line } from "react-chartjs-2";
import { CalendarClock, CheckCircle2, ChevronDown, ChevronUp, Lightbulb, LoaderCircle, Quote, Sparkles, AlertTriangle, TrendingUp } from "lucide-react";
import { getInterviewSessionDetail, listInterviewSessions } from "../features/timeline/api/timelineApi";
import { generateTimelineInsight } from "../features/timeline/api/timelineAiApi";
import type { TimelineInsightResult } from "../features/timeline/api/timelineAiApi";
import type { InterviewSessionRecordDetail, InterviewSessionRecordSummary } from "../features/timeline/model/timeline.types";
import { listProjects, listSelfIntroductions } from "../features/resume/api/resumeApi";
import { getSubscriptionStatus } from "../features/subscription/api/subscriptionApi";
import { PageHeading } from "../shared/components/PageHeading";

// "유료 결제했다는 전제하에" 최근 몇 개 세션까지 상세 조회해서 인사이트에 쓸지 - 너무 많이
// 조회하면 요청이 무거워지고, 인사이트 자체도 "최근 경향"이 아니라 "역대 평균"처럼 밍밍해진다
// (insight.py의 _MAX_SESSIONS_CONSIDERED와 같은 판단, 프론트 쪽은 상세 조회 비용까지 고려해
// 더 적게 잡았다).
const INSIGHT_SESSION_LIMIT = 5;

// 2026-08-10: 개인 타임라인 페이지(태스크 #68) - 저장된 모의면접 세션(태스크 #66/#67로 새로
// 생긴 InterviewSessionRecord)을 시간순으로 보여준다. chart.js는 StatisticsDashboard.tsx가
// 이미 쓰고 있어서(Bar) 같은 라이브러리로 통일 - 여기선 점수 추이라 Line을 대신 등록한다.
ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend);

// MockInterviewPage.tsx의 INTERVIEW_ROLE_OPTIONS와 코드가 반드시 같아야 한다 - 그쪽에서
// 새 분야를 추가하면 여기도 같이 고쳐야 라벨이 깨지지 않는다.
const ROLE_LABELS: Record<string, string> = {
  BACKEND: "백엔드",
  FRONTEND: "프론트엔드",
  FULLSTACK: "풀스택",
  MOBILE: "모바일 (iOS/Android)",
  DATA_AI: "데이터 · AI · 기타",
};

const MODE_LABELS: Record<string, string> = { camera: "카메라/마이크", chat: "채팅" };

function formatDate(iso: string): string {
  const d = new Date(iso);
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

// 2026-08-10: 태스크 #39 "이 부분 연습하기" 딥링크 - 부족한 점(반복 개선점/개별 리포트
// 개선할 점)을 보여주는 것에서 끝나지 않고, 그 자리에서 바로 같은 분야/유형으로 모의면접을
// 새로 시작할 수 있게 링크를 만든다. MockInterviewPage가 role/type 쿼리를 미리 선택해준다
// (자동 시작은 안 함). role/type이 없으면(구버전 세션 등) 그냥 분야/유형 미지정으로 연결.
function practiceLink(role: string | null, interviewType: string | null): string {
  const params = new URLSearchParams();
  if (role) params.set("role", role);
  if (interviewType) params.set("type", interviewType);
  const query = params.toString();
  return query ? `/mock-interview?${query}` : "/mock-interview";
}

export function TimelinePage() {
  const [sessions, setSessions] = useState<InterviewSessionRecordSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<InterviewSessionRecordDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [insight, setInsight] = useState<TimelineInsightResult | null>(null);
  const [insightLoading, setInsightLoading] = useState(false);
  // null = 아직 확인 중, 깜빡임(잠깐 "미구독" 문구가 떴다가 사라지는 것) 방지용.
  const [subscribed, setSubscribed] = useState<boolean | null>(null);

  useEffect(() => {
    void listInterviewSessions()
      .then(setSessions)
      .catch((e) => setErrorMessage(e instanceof Error ? e.message : "타임라인을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    void getSubscriptionStatus()
      .then((s) => setSubscribed(s.subscribed))
      .catch(() => setSubscribed(false));
  }, []);

  // 2026-08-10: 태스크 #69 "유료 결제 전제" 인사이트 - 세션이 2개 이상 쌓이면(반복 패턴을
  // 비교할 대상이 있어야 의미가 있음, insight.py의 _MIN_SESSIONS과 맞춤) 최근 세션 상세 +
  // 이력서 내용을 모아 자동으로 한 번 생성한다.
  //
  // 결제 게이트 연결(구독 기능 완성 후) - insight.py 자체 주석이 "프론트에서 API 부르기
  // 전에 구독 여부만 확인하면 된다"고 미리 설계해둔 지점이라, 여기서 subscribed 체크만
  // 추가했다(ai-server 모듈은 안 건드림 - 관심사 분리 그대로 유지). 미구독이면 아예
  // 호출하지 않는다(Gemini 호출 비용 절감 + "유료 기능" 의미 유지).
  useEffect(() => {
    if (sessions.length < 2 || !subscribed) return;
    setInsightLoading(true);
    const recentIds = sessions.slice(0, INSIGHT_SESSION_LIMIT).map((s) => s.id);
    Promise.all([
      Promise.all(recentIds.map((id) => getInterviewSessionDetail(id))),
      listSelfIntroductions(),
      listProjects(),
    ])
      .then(([details, selfIntros, projects]) =>
        generateTimelineInsight(
          details.map((d) => ({
            role: d.role,
            interviewType: d.interviewType,
            overallScore: d.overallScore,
            improvements: d.improvements,
          })),
          selfIntros.map((s) => s.content),
          projects.map((p) => ({
            title: p.title,
            roleDescription: p.roleDescription,
            problemDescription: p.problemDescription,
            solutionDescription: p.solutionDescription,
            resultDescription: p.resultDescription,
          })),
        ),
      )
      .then(setInsight)
      .catch(() => setInsight(null))
      .finally(() => setInsightLoading(false));
  }, [sessions]);

  const toggleExpand = (id: number) => {
    if (expandedId === id) {
      setExpandedId(null);
      setDetail(null);
      return;
    }
    setExpandedId(id);
    setDetail(null);
    setDetailLoading(true);
    getInterviewSessionDetail(id)
      .then(setDetail)
      .catch(() => setDetail(null))
      .finally(() => setDetailLoading(false));
  };

  // 그래프는 오래된 순 -> 최신 순(왼쪽에서 오른쪽으로 점수가 어떻게 변해왔는지 보는 용도)이라
  // 목록(최신이 위)과 반대 순서로 뒤집는다.
  const chartSessions = [...sessions].reverse().filter((s) => s.overallScore !== null);

  return (
    <>
      <PageHeading
        eyebrow="MY TIMELINE"
        title="개인 타임라인"
        body="지금까지 연습한 모의면접 결과를 한눈에 볼 수 있어요. 점수 추이와 지난 리포트를 다시 확인해보세요."
      />

      {loading && (
        <p style={{ display: "flex", alignItems: "center", gap: 8, color: "#6a7383" }}>
          <LoaderCircle className="spin" size={16} /> 불러오는 중...
        </p>
      )}
      {errorMessage && <div className="auth-error">{errorMessage}</div>}

      {!loading && !errorMessage && sessions.length === 0 && (
        <section className="panel" style={{ padding: 24 }}>
          <p style={{ margin: 0, color: "#6a7383" }}>
            아직 완료한 모의면접 기록이 없어요. AI 모의면접에서 세션을 마치고 "AI 분석"까지 받으면 여기 타임라인에 쌓여요.
          </p>
        </section>
      )}

      {sessions.length >= 2 && subscribed === false && (
        <section className="panel" style={{ padding: "20px 24px", marginBottom: 20, borderLeft: "3px solid #d8ceff" }}>
          <div className="interview-report-head" style={{ marginBottom: 8 }}>
            <TrendingUp size={16} /> 누적 인사이트
          </div>
          <p style={{ margin: "0 0 12px", color: "#6a7383", fontSize: 13, lineHeight: 1.6 }}>
            구독하면 지금까지의 모의면접 기록에서 반복적으로 지적된 점과, 이력서 내용과 연결된 맞춤 제안을 볼 수 있어요.
          </p>
          <Link to="/account" className="primary-button" style={{ display: "inline-block", textDecoration: "none" }}>
            구독하기
          </Link>
        </section>
      )}
      {insightLoading && (
        <section className="panel" style={{ padding: "18px 24px", marginBottom: 20 }}>
          <p style={{ display: "flex", alignItems: "center", gap: 8, margin: 0, color: "#6a7383", fontSize: 13 }}>
            <LoaderCircle className="spin" size={14} /> 지금까지의 기록을 종합해서 인사이트를 만드는 중이에요...
          </p>
        </section>
      )}
      {!insightLoading && subscribed && insight?.ok && (insight.recurring_points.length > 0 || insight.resume_linked_suggestion) && (
        <section className="panel" style={{ padding: "20px 24px", marginBottom: 20, borderLeft: "3px solid #596ff3" }}>
          <div className="interview-report-head" style={{ marginBottom: 14 }}>
            <TrendingUp size={16} /> 누적 인사이트
          </div>
          {insight.recurring_points.length > 0 && (
            <div className="interview-report-section">
              <h4><AlertTriangle size={14} color="#e0a233" /> 반복적으로 지적된 점</h4>
              <ul className="interview-report-list improvements">
                {insight.recurring_points.map((p, i) => <li key={i}><AlertTriangle size={14} />{p}</li>)}
              </ul>
            </div>
          )}
          {insight.resume_linked_suggestion && (
            <div className="interview-model-answer" style={{ marginTop: 6, marginBottom: insight.recurring_points.length > 0 ? 14 : 0 }}>
              <h4><Sparkles size={13} /> 이력서 기반 제안</h4>
              <p>{insight.resume_linked_suggestion}</p>
            </div>
          )}
          {insight.recurring_points.length > 0 && (
            <Link
              to={practiceLink(sessions[0]?.role ?? null, sessions[0]?.interviewType ?? null)}
              className="primary-button"
              style={{ display: "inline-block", textDecoration: "none" }}
            >
              이 부분 연습하기
            </Link>
          )}
        </section>
      )}

      {chartSessions.length >= 2 && (
        <section className="panel" style={{ padding: "20px 24px", marginBottom: 20 }}>
          <div className="panel-title" style={{ marginBottom: 12 }}>
            <div>
              <h2 style={{ margin: 0, fontSize: 15 }}>총평 점수 추이</h2>
            </div>
          </div>
          <Line
            data={{
              labels: chartSessions.map((s) => formatDate(s.createdAt)),
              datasets: [
                {
                  label: "총평 점수",
                  data: chartSessions.map((s) => s.overallScore),
                  borderColor: "#596ff3",
                  backgroundColor: "#596ff333",
                  tension: 0.3,
                  pointRadius: 4,
                },
              ],
            }}
            options={{
              responsive: true,
              plugins: { legend: { display: false } },
              scales: { y: { min: 0, max: 100, ticks: { stepSize: 20 } } },
            }}
          />
        </section>
      )}

      {sessions.map((s) => (
        <section key={s.id} className="panel" style={{ padding: "18px 20px", marginBottom: 12 }}>
          <div
            style={{ display: "flex", justifyContent: "space-between", alignItems: "center", cursor: "pointer" }}
            onClick={() => toggleExpand(s.id)}
          >
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, color: "#6a7383", fontSize: 12 }}>
                <CalendarClock size={14} /> {formatDate(s.createdAt)}
              </div>
              <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                {s.role && <span className="interview-chat-category-btn" style={{ cursor: "default" }}>{ROLE_LABELS[s.role] ?? s.role}</span>}
                {s.interviewType && <span className="interview-chat-category-btn" style={{ cursor: "default" }}>{s.interviewType}</span>}
                <span className="interview-chat-category-btn" style={{ cursor: "default" }}>{MODE_LABELS[s.interviewMode] ?? s.interviewMode}</span>
                <span className="interview-chat-category-btn" style={{ cursor: "default" }}>질문 {s.questionCount}개</span>
              </div>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
              {s.overallScore !== null && (
                <div style={{ textAlign: "center" }}>
                  <strong style={{ fontSize: 20, color: "#293349" }}>{s.overallScore}</strong>
                  <div style={{ fontSize: 10, color: "#9098a7" }}>/ 100점</div>
                </div>
              )}
              {expandedId === s.id ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
            </div>
          </div>

          {expandedId === s.id && (
            <div style={{ marginTop: 18, paddingTop: 18, borderTop: "1px solid #eef0f4" }}>
              {detailLoading && (
                <p style={{ display: "flex", alignItems: "center", gap: 8, color: "#6a7383", fontSize: 13 }}>
                  <LoaderCircle className="spin" size={14} /> 불러오는 중...
                </p>
              )}
              {!detailLoading && !detail && <p style={{ color: "#6a7383", fontSize: 13 }}>상세 정보를 불러오지 못했어요.</p>}
              {!detailLoading && detail && (
                <>
                  {detail.strengths.length > 0 && (
                    <div className="interview-report-section">
                      <h4><CheckCircle2 size={14} color="#37bf82" /> 잘한 점</h4>
                      <ul className="interview-report-list strengths">
                        {detail.strengths.map((item, i) => <li key={i}><CheckCircle2 size={14} />{item}</li>)}
                      </ul>
                    </div>
                  )}
                  {detail.improvements.length > 0 && (
                    <div className="interview-report-section">
                      <h4><AlertTriangle size={14} color="#e0a233" /> 개선할 점</h4>
                      <ul className="interview-report-list improvements">
                        {detail.improvements.map((item, i) => <li key={i}><AlertTriangle size={14} />{item}</li>)}
                      </ul>
                      <Link
                        to={practiceLink(detail.role, detail.interviewType)}
                        className="outline-button"
                        style={{ display: "inline-flex", marginTop: 10, textDecoration: "none" }}
                      >
                        이 부분 연습하기
                      </Link>
                    </div>
                  )}
                  {detail.nextSteps.length > 0 && (
                    <div className="interview-report-section" style={{ marginBottom: 0 }}>
                      <h4><Lightbulb size={14} color="#6678e8" /> 다음에 연습하면 좋을 점</h4>
                      <ul className="interview-report-list next-steps">
                        {detail.nextSteps.map((item, i) => <li key={i}><Lightbulb size={14} />{item}</li>)}
                      </ul>
                    </div>
                  )}
                  {detail.questions.length > 0 && (
                    <div style={{ marginTop: 20, paddingTop: 18, borderTop: "1px solid #eef0f4" }}>
                      <div className="interview-report-head" style={{ marginBottom: 12 }}>
                        <Quote size={16} /> 질문별 피드백
                      </div>
                      <div style={{ display: "grid", gap: 14 }}>
                        {detail.questions.map((q, i) => (
                          <div key={i} style={{ border: "1px solid #e8ebf1", borderRadius: 12, background: "#fbfcff", padding: "14px 16px" }}>
                            <strong style={{ display: "block", color: "#293349", fontSize: 13, marginBottom: 8 }}>{`Q${i + 1}. ${q.question}`}</strong>
                            {q.feedback && <p style={{ margin: 0, color: "#3a4356", fontSize: 13, lineHeight: 1.7 }}>{q.feedback}</p>}
                            {q.modelAnswer && (
                              <div className="interview-model-answer" style={{ marginTop: 10, marginBottom: 0 }}>
                                <h4><Sparkles size={13} /> 모범 답안 예시</h4>
                                <p>{q.modelAnswer}</p>
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          )}
        </section>
      ))}
    </>
  );
}

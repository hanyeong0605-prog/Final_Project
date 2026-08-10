/*  커리어넷 검사 관련 API */
/* ( 성인 기준 : 직업 가치관 , 주요능력효능감 , 진로개발준비도 , 이공계전공적합도 ) */

/* 2026-08-10: 주석에 실제 API 키가 평문으로 커밋돼 있던 걸 발견해서 지웠다(보안 이슈 -
   깃 히스토리엔 남아있으니 이미 노출된 키라면 커리어넷 쪽에서 재발급받는 걸 권장).
   실제 호출은 프론트가 아니라 Spring CareerController가 대신 하고, 키는 서버 쪽
   환경변수(CAREER_API_KEY)로만 관리된다 - 아래는 참고용 엔드포인트 형태만 남긴다.
www.career.go.kr/inspct/openapi/test/questions?apikey=<CAREER_API_KEY>&q=6 (직업가치관검사 대학생/일반)
www.career.go.kr/inspct/openapi/test/questions?apikey=<CAREER_API_KEY>&q=8 (진로개발준비도검사)
www.career.go.kr/inspct/openapi/test/questions?apikey=<CAREER_API_KEY>&q=9 (이공계전공적합도
www.career.go.kr/inspct/openapi/test/questions?apikey=<CAREER_API_KEY>&q=10 (주요능력효능감검사) */

// 2026-08-10: 원래 이 페이지(진로심리검사 4종)와 CheckPage.tsx(맞춤법 검사기)가 사이드바
// 메뉴에 안 걸린 채로 따로 떠 있었다 - "각각 메뉴에 넣으면 지저분하다"는 피드백으로, 둘을
// 탭 하나짜리 페이지로 합쳐서 사이드바엔 이 페이지 하나만 노출한다(ResumePage.tsx의 탭
// 전환 패턴과 동일). CheckPage.tsx는 이 파일로 흡수됐으니 삭제했고, DashboardPage의
// "맞춤법 검사기" 버튼은 이제 ?tab=check 쿼리로 이 페이지의 두 번째 탭을 바로 연다.
import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { PageHeading } from "../shared/components/PageHeading";
import { PanelTitle } from "../shared/components/PanelTitle";
import { Clock, Copy, SpellCheck2 } from "lucide-react";

interface TestAction {
  label: string;
  path: string;
}

interface CareerTest {
  id: string;
  title: string;
  description: string;
  duration: string;
  questionCount: string;
  actions: TestAction[]; // 단일 또는 복수 버튼 지원
}

type Tab = "tests" | "check";

export function QuestionPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState<Tab>(searchParams.get("tab") === "check" ? "check" : "tests");

  // 이미지 디자인에 맞춘 4가지 진로심리검사 데이터
  const testList: CareerTest[] = [
    {
      id: "career-development",
      title: "진로개발준비도검사",
      description: "진로목표 달성을 위해 필요한 사항들에 대한 준비 정도를 알아볼 수 있습니다.",
      duration: "25~30분",
      questionCount: "35문항",
      actions: [
        { label: "검사바로가기", path: "/tests/career-development" }
      ]
    },
    {
      id: "major-efficacy",
      title: "주요능력효능감검사",
      description: "직업과 관련된 특정 능력에 대해 스스로의 자신감 정보를 알아볼 수 있습니다.",
      duration: "20분",
      questionCount: "49문항",
      actions: [
        { label: "검사바로가기", path: "/tests/major-efficacy" }
      ]
    },
    {
      id: "stem-major-suitability",
      title: "이공계전공적합도검사",
      description: "대학의 이공계 내 세부전공별 적합도를 알아볼 수 있습니다.",
      duration: "30분",
      questionCount: "107문항",
      actions: [
        { label: "검사바로가기", path: "/tests/stem-major-suitability" }
      ]
    },
    {
      id: "job-value",
      title: "직업가치관검사",
      description: "직업과 관련된 다양한 가치 중, 어떤 가치를 주요하게 만족시키고 싶은지 알아볼 수 있습니다.",
      duration: "10분",
      questionCount: "28문항",
      actions: [
        { label: "검사바로가기", path: "/tests/job-value" }
      ]
    },
  ];

  return (
    <>
      <PageHeading
        eyebrow="CAREER TOOLS"
        title="진로검사·글쓰기 도구"
        body="나에게 알맞은 진로 방향을 진단하는 심리검사와, 자기소개서 등을 다듬을 때 쓸 수 있는 맞춤법 검사기를 한곳에 모았습니다."
      />

      <div className="form-actions" style={{ marginBottom: 20 }}>
        <button className={tab === "tests" ? "primary-button" : "outline-button"} onClick={() => setTab("tests")}>
          진로심리검사
        </button>
        <button className={tab === "check" ? "primary-button" : "outline-button"} onClick={() => setTab("check")}>
          맞춤법 검사기
        </button>
      </div>

      {tab === "tests" ? (
        <section className="panel" style={{ padding: "24px" }}>
          <PanelTitle
            title="검사 목록 선택"
            subtitle="원하시는 검사를 선택하여 질문지를 확인하고 진단을 시작하세요."
          />

          {/* 2열 그리드 레이아웃 */}
          <div style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(480px, 1fr))",
            gap: "24px",
            marginTop: "20px"
          }}>
            {testList.map((test) => (
              <div
                key={test.id}
                style={{
                  background: "#ffffff",
                  border: "1px solid #e2e8f0",
                  borderRadius: "12px",
                  overflow: "hidden",
                  boxShadow: "0 2px 4px rgba(0,0,0,0.02)",
                  display: "flex",
                  flexDirection: "column"
                }}
              >
                {/* 카드 상단 헤더 영역 (연한 회색 배경 + 타이틀 + 우측 버튼들) */}
                <div style={{
                  background: "#f8fafc",
                  borderBottom: "1px solid #e2e8f0",
                  padding: "18px 24px",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between"
                }}>
                  <h3 style={{ fontSize: "18px", fontWeight: "bold", color: "#1e293b", margin: 0 }}>
                    {test.title}
                  </h3>

                  <div style={{ display: "flex", gap: "8px" }}>
                    {test.actions.map((action, idx) => (
                      <button
                        key={idx}
                        onClick={() => navigate(action.path)}
                        style={{
                          background: "#3b82f6",
                          color: "#ffffff",
                          border: "none",
                          borderRadius: "6px",
                          padding: "6px 14px",
                          fontSize: "13px",
                          fontWeight: "600",
                          cursor: "pointer",
                          transition: "opacity 0.2s"
                        }}
                        onMouseEnter={(e) => { e.currentTarget.style.opacity = "0.9"; }}
                        onMouseLeave={(e) => { e.currentTarget.style.opacity = "1"; }}
                      >
                        {action.label}
                      </button>
                    ))}
                  </div>
                </div>

                {/* 카드 하단 바디 영역 (설명 및 소요시간/문항수) */}
                <div style={{ padding: "24px", display: "flex", flexDirection: "column", justifyContent: "space-between", flex: 1 }}>
                  <p style={{ fontSize: "14px", color: "#475569", lineHeight: "1.6", marginBottom: "24px", minHeight: "44px" }}>
                    {test.description}
                  </p>

                  <div style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "16px",
                    color: "#64748b",
                    fontSize: "13px",
                    fontWeight: "500"
                  }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                      <Clock size={16} color="#3b82f6" />
                      <span>소요시간 {test.duration}</span>
                    </div>
                    <span>{test.questionCount}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>
      ) : (
        <SpellCheckPanel />
      )}
    </>
  );
}

// 2026-08-10: 원래 CheckPage.tsx의 로직(POST /api/checks/correct, Bareun AI 맞춤법 교정)을
// 그대로 옮겨왔다 - API 호출/상태 관리는 손대지 않고, 스타일만 이 페이지의 다른 탭과
// 어울리게 panel/form-section 클래스로 맞췄다(원래는 인라인 스타일로 따로 놀았음).
function SpellCheckPanel() {
  const [inputText, setInputText] = useState("");
  const [resultText, setResultText] = useState("");
  const [copySuccess, setCopySuccess] = useState(false);
  const [checking, setChecking] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const inputLengthWithSpaces = inputText.length;
  const inputLengthWithoutSpaces = inputText.replace(/\s+/g, "").length;
  const resultLengthWithSpaces = resultText.length;
  const resultLengthWithoutSpaces = resultText.replace(/\s+/g, "").length;

  const handleCorrect = async () => {
    setChecking(true);
    setErrorMessage(null);
    try {
      const response = await fetch("/api/checks/correct", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ q: inputText }),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const responseData = await response.json().catch(() => response.text());
      const data = typeof responseData === "string" ? JSON.parse(responseData) : responseData;
      setResultText(data.revised);
      setCopySuccess(false);
    } catch {
      setErrorMessage("교정 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    } finally {
      setChecking(false);
    }
  };

  const handleCopy = async () => {
    if (!resultText) return;
    try {
      await navigator.clipboard.writeText(resultText);
      setCopySuccess(true);
      setTimeout(() => setCopySuccess(false), 2000);
    } catch {
      setErrorMessage("클립보드 복사에 실패했습니다.");
    }
  };

  return (
    <section className="panel" style={{ padding: 24 }}>
      <PanelTitle title="맞춤법 검사기" subtitle="자기소개서나 답변 초안을 붙여넣고 맞춤법을 교정해보세요." />

      {errorMessage && <div className="auth-error" style={{ marginTop: 12 }}>{errorMessage}</div>}

      <div className="form-section" style={{ marginTop: 16 }}>
        <div className="form-fields">
          <label className="wide">
            교정할 문장
            <textarea
              rows={6}
              placeholder="교정할 문장을 입력하세요..."
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
            />
          </label>
        </div>
        <div style={{ textAlign: "right", fontSize: 12, color: "#9098a7", marginTop: 6 }}>
          공백 포함 {inputLengthWithSpaces}자 / 공백 미포함 {inputLengthWithoutSpaces}자
        </div>
      </div>

      <div className="form-actions">
        <button className="primary-button" onClick={() => void handleCorrect()} disabled={checking || !inputText.trim()}>
          <SpellCheck2 size={14} /> {checking ? "교정 중..." : "맞춤법 교정하기"}
        </button>
      </div>

      <div className="form-section" style={{ marginTop: 16 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
          <h3 style={{ margin: 0, fontSize: 14 }}>교정 결과</h3>
          {resultText && (
            <button className="outline-button" onClick={() => void handleCopy()}>
              <Copy size={13} /> {copySuccess ? "복사 완료!" : "전체 복사"}
            </button>
          )}
        </div>
        <div style={{
          padding: 15,
          background: "#f8f9fa",
          border: "1px solid #dfe4ec",
          borderRadius: 8,
          minHeight: 80,
          whiteSpace: "pre-wrap",
          fontSize: 13,
          color: "#293349",
        }}>
          {resultText || "교정된 결과가 여기에 표시됩니다."}
        </div>
        {resultText && (
          <div style={{ textAlign: "right", fontSize: 12, color: "#9098a7", marginTop: 6 }}>
            교정본 공백 포함 {resultLengthWithSpaces}자 / 공백 미포함 {resultLengthWithoutSpaces}자
          </div>
        )}
      </div>
    </section>
  );
}

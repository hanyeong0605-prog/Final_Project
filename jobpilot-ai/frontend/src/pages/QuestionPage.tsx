/*  커리어넷 검사 관련 API */
/* ( 성인 기준 : 직업 가치관 , 주요능력효능감 , 진로개발준비도 , 이공계전공적합도 ) */

/* www.career.go.kr/inspct/openapi/test/questions?apikey=e54274d54718d12457fbb8a202f83d6e&q=6 (직업가치관검사 대학생/일반)
www.career.go.kr/inspct/openapi/test/questions?apikey=e54274d54718d12457fbb8a202f83d6e&q=8 (진로개발준비도검사)
www.career.go.kr/inspct/openapi/test/questions?apikey=e54274d54718d12457fbb8a202f83d6e&q=9 (이공계전공적합도
www.career.go.kr/inspct/openapi/test/questions?apikey=e54274d54718d12457fbb8a202f83d6e&q=10 (주요능력효능감검사) */

import { useNavigate } from "react-router-dom";
import { PageHeading } from "../shared/components/PageHeading";
import { PanelTitle } from "../shared/components/PanelTitle";
import { Clock } from "lucide-react";

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

export function QuestionPage() {
  const navigate = useNavigate();

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
        { label: "활동1", path: "/tests/job-value" },
        { label: "활동2", path: "/tests/job-value" },
        { label: "검사바로가기", path: "/tests/job-value" }
      ]
    },
  ];

  return (
    <>
      <PageHeading 
        eyebrow="CAREER PSYCHOLOGICAL TESTS" 
        title="진로심리검사 질문지 센터" 
        body="나에게 알맞은 진로 방향과 직업 가치관을 진단할 수 있는 심리검사 선택 페이지입니다." 
      />

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
                
                {/* 버튼 그룹 (활동1, 활동2, 검사바로가기 등) */}
                <div style={{ display: "flex", gap: "8px" }}>
                  {test.actions.map((action, idx) => {
                    const isPrimary = action.label === "검사바로가기";
                    return (
                      <button
                        key={idx}
                        onClick={() => navigate(action.path)}
                        style={{
                          background: isPrimary ? "#3b82f6" : "#ffffff",
                          color: isPrimary ? "#ffffff" : "#475569",
                          border: isPrimary ? "none" : "1px solid #cbd5e1",
                          borderRadius: "6px",
                          padding: "6px 14px",
                          fontSize: "13px",
                          fontWeight: "600",
                          cursor: "pointer",
                          transition: "all 0.2s"
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.opacity = "0.9";
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.opacity = "1";
                        }}
                      >
                        {action.label}
                      </button>
                    );
                  })}
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
    </>
  );
}

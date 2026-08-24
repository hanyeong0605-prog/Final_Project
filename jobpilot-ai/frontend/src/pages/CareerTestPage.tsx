// 2026-08-10: 원래 Test1~4.tsx 4개 파일로 나뉘어 있던 커리어넷 심리검사 4종(진로개발준비도/
// 주요능력효능감/이공계전공적합도/직업가치관)을 파일 하나로 합쳤다 - "테스트 파일 4개가
// 지저분해 보인다"는 피드백. 4개가 거의 완전히 같은 화면 로직(문항 렌더링/라디오 선택/제출)
// 이었고 실제로 다른 건 검사 번호(qestrnSeq)·제목·제출 payload 형식뿐이었어서, 그 차이만
// CAREER_TESTS 설정으로 빼고 나머지 로직은 하나로 합쳤다.
//
// URL은 그대로 유지했다(tests/career-development 등) - QuestionPage.tsx의 카드 링크를 안
// 고쳐도 되게, 라우터에서 `tests/:testKey`로 한 경로만 등록하고 이 컴포넌트가 testKey로
// 아래 설정을 찾아 렌더링한다(router.tsx 참고).
//
// 답변 제출 형식 차이 - 원래 Test1~3(진로개발준비도/주요능력효능감/이공계전공적합도)은
// "1,2,3" 처럼 쉼표로 구분한 점수 문자열이었는데, Test4(직업가치관검사)만 "B1=2 B2=3"
// 처럼 문항번호를 붙인 공백 구분 형식이었다(커리어넷 API가 검사마다 다른 형식을 요구함) -
// 이 차이를 answerFormat으로 남겨서 실수로 합치면서 깨지지 않게 했다.
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { fetchTestQuestions, submitTestReport } from "../features/career/api/careerAPI";
import { PageHeading } from "../shared/components/PageHeading";
import { DataStatePanel } from "../shared/components/DataStatePanel";

interface CareerTestConfig {
  testId: string; // 커리어넷 qestrnSeq
  title: string;
  answerFormat: "csv" | "b-prefixed";
  includeSchool: boolean; // Test1(진로개발준비도)만 원래 school 필드가 빠져 있었다 - 그대로 유지
}

const CAREER_TESTS: Record<string, CareerTestConfig> = {
  "career-development": { testId: "8", title: "진로개발준비도검사", answerFormat: "csv", includeSchool: false },
  "major-efficacy": { testId: "10", title: "주요능력효능감검사", answerFormat: "csv", includeSchool: true },
  "stem-major-suitability": { testId: "9", title: "이공계전공적합도검사", answerFormat: "csv", includeSchool: true },
  "job-value": { testId: "6", title: "직업가치관검사", answerFormat: "b-prefixed", includeSchool: true },
};

export function CareerTestPage() {
  const { testKey } = useParams<{ testKey: string }>();
  const config = testKey ? CAREER_TESTS[testKey] : undefined;

  const [questions, setQuestions] = useState<any[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [reportUrl, setReportUrl] = useState<string | null>(null);
  const [answers, setAnswers] = useState<{ [key: number]: string }>({});

  useEffect(() => {
    if (!config) {
      setLoading(false);
      return;
    }
    // 검사를 바꿔서 들어와도(직접 URL 이동 등) 이전 검사 상태가 안 남게 초기화한다.
    setLoading(true);
    setAnswers({});
    setReportUrl(null);
    fetchTestQuestions(config.testId)
      .then((res) => {
        setQuestions(res.RESULT || []);
        setLoading(false);
      })
      .catch(() => {
        setLoading(false);
      });
  }, [config?.testId]);

  const handleSelectAnswer = (questionIndex: number, score: string) => {
    setAnswers((prev) => ({ ...prev, [questionIndex]: score }));
  };

  const handleSubmit = async () => {
    if (!config) return;
    if (Object.keys(answers).length < questions.length) {
      alert("모든 문항에 답변해주세요!");
      return;
    }

    setIsSubmitting(true);
    try {
      const answerString =
        config.answerFormat === "b-prefixed"
          ? questions.map((q, idx) => `B${q.qitemNo ?? idx + 1}=${answers[idx]}`).join(" ")
          : questions.map((_, idx) => answers[idx]).join(",");

      const requestPayload: Record<string, unknown> = {
        qestrnSeq: config.testId,
        trgetSe: "100209",
        gender: "100324",
        grade: "1",
        startDtm: Date.now(),
        answers: answerString,
      };
      if (config.includeSchool) requestPayload.school = "일반";

      const res = await submitTestReport(requestPayload);
      if (res.SUCC_YN === "Y" && res.RESULT?.url) {
        setReportUrl(res.RESULT.url);
      } else {
        alert("결과 생성에 실패했습니다: " + (res.ERROR_REASON || "알 수 없는 오류"));
      }
    } catch (err) {
      console.error(err);
      alert("서버 통신 중 오류가 발생했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!config) {
    return (
      <>
        <PageHeading eyebrow="CAREER TEST" title="검사를 찾을 수 없습니다" body="진로검사·글쓰기 도구 페이지에서 다시 선택해주세요." />
        <DataStatePanel state="empty" emptyTitle="알 수 없는 검사" emptyBody="유효하지 않은 검사 경로입니다." />
      </>
    );
  }

  return (
    <>
      <PageHeading eyebrow="CAREER TEST" title={config.title} body="문항을 꼼꼼히 읽고 자신에게 알맞은 번호를 선택해주세요." />

      <div style={{ padding: "24px", background: "#ffffff", borderRadius: "12px", border: "1px solid #e2e8f0" }}>
        {loading && <DataStatePanel state="loading" />}

        {!loading && questions.length === 0 && (
          <DataStatePanel state="empty" emptyTitle="문항이 없습니다" emptyBody="데이터를 불러오지 못했습니다." />
        )}

        {!loading && questions.length > 0 && (
          <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
            {questions.map((item, index) => {
              const answerOptions = [
                { text: item.answer01, score: item.answerScore01 },
                { text: item.answer02, score: item.answerScore02 },
                { text: item.answer03, score: item.answerScore03 },
                { text: item.answer04, score: item.answerScore04 },
                item.answer05 ? { text: item.answer05, score: item.answerScore05 } : null,
              ].filter((opt): opt is { text: string; score: string } => Boolean(opt?.text && opt.score));

              return (
                <div key={index} style={{ paddingBottom: "20px", borderBottom: "1px solid #f1f5f9" }}>
                  <p style={{ fontWeight: "600", color: "#1e293b", marginBottom: "12px" }}>
                    {index + 1}. {item.question}
                  </p>

                  <div style={{ display: "flex", flexDirection: "column", gap: "8px", paddingLeft: "10px" }}>
                    {answerOptions.map((opt, optIdx) => (
                      <label key={optIdx} style={{ display: "flex", alignItems: "center", gap: "8px", cursor: "pointer", fontSize: "14px", color: "#475569" }}>
                        <input
                          type="radio"
                          name={`question-${index}`}
                          value={opt.score}
                          checked={answers[index] === opt.score}
                          onChange={() => handleSelectAnswer(index, opt.score)}
                        />
                        {opt.text}
                      </label>
                    ))}
                  </div>
                </div>
              );
            })}

            <div style={{ textAlign: "center", marginTop: "20px" }}>
              <button
                onClick={handleSubmit}
                disabled={isSubmitting}
                style={{ padding: "12px 24px", backgroundColor: "#5B92F3", color: "#ffffff", border: "none", borderRadius: "8px", fontWeight: "600", cursor: "pointer" }}
              >
                {isSubmitting ? "제출 중..." : "결과 제출하기"}
              </button>
            </div>

            {reportUrl && (
              <div style={{ textAlign: "center", marginTop: "16px" }}>
                <p>검사가 완료되었습니다.</p>
                <a href={reportUrl} target="_blank" rel="noreferrer" style={{ color: "#5B92F3", fontWeight: "600" }}>결과 리포트 보기 열기</a>
              </div>
            )}
          </div>
        )}
      </div>
    </>
  );
}

import { useEffect, useState } from "react";
import { fetchTestQuestions, submitTestReport } from "../features/career/api/careerAPI";
import { PageHeading } from "../shared/components/PageHeading";
import { DataStatePanel } from "../shared/components/DataStatePanel";

export function Test4() {
  const [questions, setQuestions] = useState<any[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [reportUrl, setReportUrl] = useState<string | null>(null);
  
  // 사용자의 응답을 저장할 상태 (문항인덱스: 선택점수)
  const [answers, setAnswers] = useState<{ [key: number]: string }>({});

  useEffect(() => {
    // 6번 검사: 직업가치관검사
    fetchTestQuestions("6")
      .then((res) => {
        setQuestions(res.RESULT || []);
        setLoading(false);
      })
      .catch(() => {
        setLoading(false);
      });
  }, []);

  // 사용자가 보기를 선택했을 때 실행되는 함수
  const handleSelectAnswer = (questionIndex: number, score: string) => {
    setAnswers((prev) => ({
      ...prev,
      [questionIndex]: score,
    }));
  };

  // 결과 제출 버튼을 눌렀을 때
  const handleSubmit = async () => {
    if (Object.keys(answers).length < questions.length) {
      alert("모든 문항에 답변해주세요!");
      return;
    }

    setIsSubmitting(true);

    try {
      // 직업가치관검사(6번) 답변 형식: "1=값 2=값 3=값" (공백으로 구분)
      const answerString = questions.map((_, idx) => `${idx + 1}=${answers[idx]}`).join(" ");

      const requestPayload = {
        qestrnSeq: "6",
        trgetSe: "100209", 
        gender: "100324",
        school: "일반",
        grade: "1",
        startDtm: Date.now(),
        answers: answerString,
      };

      console.log("직업가치관검사 전송 페이로드:", requestPayload);

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

  return (
    <>
      <PageHeading 
        eyebrow="CAREER TEST" 
        title="직업가치관검사" 
        body="문항을 꼼꼼히 읽고 자신에게 알맞은 번호를 선택해주세요." 
      />

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
              ].filter(opt => opt && opt.text);

              return (
                <div key={index} style={{ paddingBottom: "20px", borderBottom: "1px solid #f1f5f9" }}>
                  <p style={{ fontWeight: "600", color: "#1e293b", marginBottom: "12px" }}>
                    {index + 1}. {item.question}
                  </p>
                  
                  {/* 보기 목록 렌더링 */}
                  <div style={{ display: "flex", flexDirection: "column", gap: "8px", paddingLeft: "10px" }}>
                    {answerOptions.map((opt, optIdx) => (
                      <label key={optIdx} style={{ display: "flex", alignItems: "center", gap: "8px", cursor: "pointer", fontSize: "14px", color: "#475569" }}>
                        <input 
                          type="radio" 
                          name={`question-${index}`} 
                          value={opt?.score}
                          checked={answers[index] === opt?.score}
                          onChange={() => handleSelectAnswer(index, opt!.score)}
                        />
                        {opt?.text}
                      </label>
                    ))}
                  </div>
                </div>
              );
            })}

            {/* 제출 버튼 */}
            <div style={{ textAlign: "center", marginTop: "20px" }}>
              <button 
                onClick={handleSubmit}
                disabled={isSubmitting}
                style={{ padding: "12px 24px", backgroundColor: "#2563eb", color: "#ffffff", border: "none", borderRadius: "8px", fontWeight: "600", cursor: "pointer" }}
              >
                {isSubmitting ? "제출 중..." : "결과 제출하기"}
              </button>
            </div>

            {reportUrl && (
              <div style={{ textAlign: "center", marginTop: "16px" }}>
                <p>검사가 완료되었습니다.</p>
                <a href={reportUrl} target="_blank" rel="noreferrer" style={{ color: "#2563eb", fontWeight: "600" }}>결과 리포트 보기 열기</a>
              </div>
            )}
          </div>
        )}
      </div>
    </>
  );
}

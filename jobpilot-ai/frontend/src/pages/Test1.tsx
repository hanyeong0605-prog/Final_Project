import { useEffect, useState } from "react";
import { fetchTestQuestions } from "../features/career/api/careerAPI";
import { PageHeading } from "../shared/components/PageHeading";
import { DataStatePanel } from "../shared/components/DataStatePanel";

export function Test1() {
  const [questions, setQuestions] = useState<any[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  
  // 사용자의 응답을 저장할 상태 (예: { 0: "3", 1: "4", ... } -> 문항인덱스: 선택점수)
  const [answers, setAnswers] = useState<{ [key: number]: string }>({});

  useEffect(() => {
    // 진로개발준비도검사 번호 '8'번 요청
    fetchTestQuestions("8")
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
  const handleSubmit = () => {
    if (Object.keys(answers).length < questions.length) {
      alert("모든 문항에 답변해주세요!");
      return;
    }
    console.log("제출된 응답 데이터:", answers);
    // 여기에 나중에 결과 계산 페이지로 넘어가거나 백엔드로 점수를 보내는 로직 추가
    alert("설문이 완료되었습니다!");
  };

  return (
    <>
      <PageHeading 
        eyebrow="CAREER TEST" 
        title="진로개발준비도검사" 
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
              // 존재하는 보기들만 동적으로 추출 (answer01, answer02, answer03 ...)
              const answerOptions = [
                { text: item.answer01, score: item.answerScore01 },
                { text: item.answer02, score: item.answerScore02 },
                { text: item.answer03, score: item.answerScore03 },
                { text: item.answer04, score: item.answerScore04 },
                item.answer05 ? { text: item.answer05, score: item.answerScore05 } : null,
              ].filter(opt => opt && opt.text); // 빈 보기 제거

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
                style={{ padding: "12px 24px", backgroundColor: "#2563eb", color: "#ffffff", border: "none", borderRadius: "8px", fontWeight: "600", cursor: "pointer" }}
              >
                결과 제출하기
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
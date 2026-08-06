// 진로넷 문항 조회 API
export async function fetchTestQuestions(testQueryNumber: string) {
  try {
    const response = await fetch(`/api/tests/questions/${testQueryNumber}`);
    if (!response.ok) {
      throw new Error("네트워크 응답이 올바르지 않습니다.");
    }
    const data = await response.json();
    return data; // 커리어넷에서 받아온 문항 데이터
  } catch (error) {
    console.error("설문지 데이터를 불러오는 데 실패했습니다:", error);
    throw error;
  }
}

// 👇 결과 제출 및 리포트 URL 받아오기 API 추가
export async function submitTestReport(reportData: any) {
  try {
    const response = await fetch('/api/tests/report', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(reportData),
    });

    if (!response.ok) {
      throw new Error("결과 전송 실패");
    }

    const data = await response.json();
    return data; // 커리어넷에서 준 결과(url 등)
  } catch (error) {
    console.error("결과를 제출하는 중 오류가 발생했습니다:", error);
    throw error;
  }
}
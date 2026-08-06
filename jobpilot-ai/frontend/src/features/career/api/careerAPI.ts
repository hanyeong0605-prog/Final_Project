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
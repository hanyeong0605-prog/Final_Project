export async function fetchCheck() {
    try {
        const response = await fetch(`/api/correct`);
        if (!response.ok) {
            throw new Error("네트워크 응답이 올바르지 않습니다.");
        }
        const data = await response.json();
        return data;
    } catch (error) {
        console.error("데이터를 불러오는 데 실패했습니다:", error);
        throw error;
    }
}
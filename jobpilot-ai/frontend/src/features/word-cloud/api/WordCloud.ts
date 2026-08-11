export interface WordCloudResponse {
  importance: 'all' | 'required' | 'preferred';
  total_records: number;
  image_data: string;
}

// Production uses the same HTTPS origin; Nginx forwards this path internally.
const ML_API_BASE_URL = import.meta.env.VITE_ML_API_URL || '/wordcloud-api';
const REQUEST_TIMEOUT_MS = 10_000;

export const getWordCloud = async (importance: string): Promise<WordCloudResponse> => {
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(`${ML_API_BASE_URL}/api/wordcloud?importance=${importance}`, {
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error('워드클라우드 데이터를 불러오지 못했습니다.');
    }
    if (!response.headers.get('content-type')?.includes('application/json')) {
      throw new Error('워드클라우드 API가 JSON을 반환하지 않았습니다.');
    }
    return response.json();
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error('워드클라우드 서버가 10초 안에 응답하지 않았습니다.');
    }
    throw error;
  } finally {
    window.clearTimeout(timeoutId);
  }
};

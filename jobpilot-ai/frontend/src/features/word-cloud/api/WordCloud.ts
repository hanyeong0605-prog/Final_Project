export interface WordCloudResponse {
  importance: 'all' | 'required' | 'preferred';
  total_records: number;
  image_data: string;
}

// Production uses the same HTTPS origin; Nginx forwards this path internally.
const ML_API_BASE_URL = import.meta.env.VITE_ML_API_URL || '/wordcloud-api';

export const getWordCloud = async (importance: string): Promise<WordCloudResponse> => {
  const response = await fetch(`${ML_API_BASE_URL}/api/wordcloud?importance=${importance}`);
  
  if (!response.ok) {
    throw new Error('WordCloud 데이터를 불러오는 데 실패했습니다.');
  }
  return response.json();
};

/* 이력서 맞춤법 검사기 쪽? 페이지 
   BARUN_API_KEY   */



import React, { useState } from 'react';
import axios from 'axios';

function CheckPage() {
    const [inputText, setInputText] = useState('');     // 사용자가 입력한 텍스트
    const [resultText, setResultText] = useState('');   // 교정된 텍스트 결과

    // 검사 버튼을 눌렀을 때 실행되는 함수
    const handleCorrect = async () => {
        try {
            // 백엔드 API 또는 바른 API로 요청 전송
            const response = await axios.post('/api/correct', {
                document: {
                    content: inputText,
                    language: 'ko-KR'
                },
                encoding_type: 'UTF32',
                config: {
                    enable_sentence_check: true
                }
            });

            // 바른 API 응답값 중 교정된 문장(revised)을 결과 상태에 저장
            setResultText(response.data.revised);
        } catch (error) {
            console.error('교정 중 오류가 발생했습니다:', error);
        }
    };

    return (
        <div style={{ padding: '20px', maxWidth: '600px', margin: '0 auto' }}>
            <h2>맞춤법 검사기</h2>

            {/* 1. 입력 영역 */}
            <textarea
                rows={6}
                style={{ width: '100%', marginBottom: '10px', padding: '10px' }}
                placeholder="교정할 문장을 입력하세요..."
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
            />

            {/* 2. 교정 실행 버튼 */}
            <button
                onClick={handleCorrect}
                style={{ padding: '10px 20px', backgroundColor: '#007bff', color: '#fff', border: 'none', cursor: 'pointer' }}
            >
                맞춤법 교정하기
            </button>

            {/* 3. 출력 영역 */}
            <div style={{ marginTop: '20px' }}>
                <h3>교정 결과:</h3>
                <div style={{ padding: '15px', backgroundColor: '#f8f9fa', border: '1px solid #ddd', minHeight: '80px' }}>
                    {resultText || '교정된 결과가 여기에 표시됩니다.'}
                </div>
            </div>
        </div>
    );
}

export default CheckPage;
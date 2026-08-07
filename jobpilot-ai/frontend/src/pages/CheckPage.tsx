import React, { useState } from 'react';

function CheckPage() {
    const [inputText, setInputText] = useState('');     
    const [resultText, setResultText] = useState('');   
    const [copySuccess, setCopySuccess] = useState(false);

    // 1. 입력 글자 수 계산
    const inputLengthWithSpaces = inputText.length;                             
    const inputLengthWithoutSpaces = inputText.replace(/\s+/g, '').length;      

    // 2. 교정 결과 글자 수 계산 (결과가 있을 때만 계산)
    const resultLengthWithSpaces = resultText.length;
    const resultLengthWithoutSpaces = resultText.replace(/\s+/g, '').length;

    const handleCorrect = async () => {
        try {
            const response = await fetch('/api/checks/correct', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ q: inputText }),
            });

            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const responseData = await response.json().catch(() => response.text());
            const data = typeof responseData === 'string' ? JSON.parse(responseData) : responseData;
            setResultText(data.revised);
            setCopySuccess(false);
        } catch (error) {
            console.error('교정 중 오류가 발생했습니다:', error);
            setResultText('교정 중 오류가 발생했습니다.');
        }
    };

    const handleCopy = async () => {
        if (!resultText) return;
        
        try {
            await navigator.clipboard.writeText(resultText);
            setCopySuccess(true);
            setTimeout(() => {
                setCopySuccess(false);
            }, 2000);
        } catch (err) {
            console.error('복사 실패:', err);
            alert('클립보드 복사에 실패했습니다.');
        }
    };

    return (
        <div style={{ padding: '20px', maxWidth: '600px', margin: '0 auto' }}>
            <h2>맞춤법 검사기</h2>

            {/* 입력 영역 */}
            <textarea
                rows={6}
                style={{ width: '100%', marginBottom: '5px', padding: '10px' }}
                placeholder="교정할 문장을 입력하세요..."
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
            />

            {/* 입력 글자 수 표시 */}
            <div style={{ textAlign: 'right', fontSize: '13px', color: '#666', marginBottom: '10px' }}>
                공백 포함: <strong>{inputLengthWithSpaces}자</strong> / 공백 미포함: <strong>{inputLengthWithoutSpaces}자</strong>
            </div>

            {/* 교정 실행 버튼 */}
            <button
                onClick={handleCorrect}
                style={{ padding: '10px 20px', backgroundColor: '#007bff', color: '#fff', border: 'none', cursor: 'pointer' }}
            >
                맞춤법 교정하기
            </button>

            {/* 출력 영역 및 복사 버튼 */}
            <div style={{ marginTop: '20px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                    <h3>교정 결과:</h3>
                    
                    {resultText && (
                        <button
                            onClick={handleCopy}
                            style={{
                                padding: '6px 12px',
                                backgroundColor: copySuccess ? '#28a745' : '#6c757d',
                                color: '#fff',
                                border: 'none',
                                cursor: 'pointer',
                                borderRadius: '4px',
                                fontSize: '14px'
                            }}
                        >
                            {copySuccess ? '복사 완료! ✓' : '전체 복사'}
                        </button>
                    )}
                </div>

                <div style={{ padding: '15px', backgroundColor: '#f8f9fa', border: '1px solid #ddd', minHeight: '80px', whiteSpace: 'pre-wrap' }}>
                    {resultText || '교정된 결과가 여기에 표시됩니다.'}
                </div>

                {/* 👇 교정 결과 글자 수 표시 (결과가 있을 때만 하단에 노출) */}
                {resultText && (
                    <div style={{ textAlign: 'right', fontSize: '13px', color: '#666', marginTop: '5px' }}>
                        교정본 공백 포함: <strong>{resultLengthWithSpaces}자</strong> / 공백 미포함: <strong>{resultLengthWithoutSpaces}자</strong>
                    </div>
                )}
            </div>
        </div>
    );
}

export default CheckPage;

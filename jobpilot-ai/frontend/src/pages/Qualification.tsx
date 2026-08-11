import React, { useState, useEffect } from 'react';

interface Qualification {
    jmfldnm: string | null;
    qualgbnm: string | null;
    obligfldnm: string | null;
}

export default function Qualification() {
    const [qualifications, setQualifications] = useState<Qualification[]>([]);
    const [loading, setLoading] = useState(true);
    
    const [selectedTab, setSelectedTab] = useState<string>('전체');
    const [searchTerm, setSearchTerm] = useState<string>('');

    useEffect(() => {
        async function fetchQualifications() {
            try {
                const response = await fetch('/api/qnet/qualifications');
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                const data: Qualification[] = await response.json();
                setQualifications(data);
            } catch (error) {
                console.error("API 요청 실패:", error);
            } finally {
                setLoading(false);
            }
        }

        fetchQualifications();
    }, []);

    const categories = ['전체', ...Array.from(new Set(qualifications.map(item => item.obligfldnm).filter(Boolean)))];

    const filteredQualifications = qualifications.filter(item => {
        const matchesTab = selectedTab === '전체' || item.obligfldnm === selectedTab;
/* obligfldnm 정보통신 */
        const term = searchTerm.toLowerCase().trim();
        const matchesSearch = 
            (item.jmfldnm && item.jmfldnm.toLowerCase().includes(term)) ||
            (item.obligfldnm && item.obligfldnm.toLowerCase().includes(term));

        return matchesTab && matchesSearch;
    });

    // 💡 핵심: 검색어도 없고, 탭도 '전체' 상태인 초기 진입 상태인지 확인
    const isInitialState = searchTerm.trim() === '' && selectedTab === '전체';

    return (
        <div style={{ padding: '20px', maxWidth: '800px', margin: '0 auto' }}>
            <h1>자격 종목 조회</h1>

            {/* 검색 입력창 영역 */}
            <div style={{ marginBottom: '20px' }}>
                <input
                    type="text"
                    placeholder="자격증 이름 또는 분야를 입력하세요 (예: 정보처리, 건축 등)"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    style={{
                        width: '100%',
                        padding: '12px 15px',
                        fontSize: '16px',
                        border: '1px solid #ccc',
                        borderRadius: '6px',
                        outline: 'none',
                        boxSizing: 'border-box'
                    }}
                />
            </div>

            {/* 탭 버튼 영역 (일단 없어도 될 거 같아서 주석 처리)
            <div style={{ display: 'flex', gap: '10px', marginBottom: '30px', flexWrap: 'wrap' }}>
                {categories.map((category) => (
                    <button
                        key={category}
                        onClick={() => setSelectedTab(category!)}
                        style={{
                            padding: '8px 16px',
                            cursor: 'pointer',
                            backgroundColor: selectedTab === category ? '#007bff' : '#f8f9fa',
                            color: selectedTab === category ? '#fff' : '#333',
                            border: '1px solid #ccc',
                            borderRadius: '4px',
                            fontWeight: selectedTab === category ? 'bold' : 'normal'
                        }}
                    >
                        {category}
                    </button>
                ))}
            </div>  */}


            {/* 자격증 목록 출력 영역 */}
            {loading ? (
                <p>데이터를 불러오는 중입니다...</p>
            ) : isInitialState ? (
                // 초기 상태일 때 보여줄 안내 화면
                <div style={{ textAlign: 'center', padding: '50px 0', color: '#666' }}>
                    <h3>원하시는 자격증을 검색해주세요.</h3>
                    <p style={{ fontSize: '14px', color: '#888', marginTop: '10px' }}>
                        총 {qualifications.length}개의 국가자격 종목이 등록되어 있습니다.
                    </p>
                </div>
            ) : filteredQualifications.length === 0 ? (
                <p style={{ textAlign: 'center', padding: '30px 0', color: '#888' }}>검색 결과가 없습니다.</p>
            ) : (
                <ul style={{ listStyle: 'none', padding: 0 }}>
                    {filteredQualifications.map((item, index) => (
                        <li key={index} style={{ padding: '12px', borderBottom: '1px solid #eee', marginBottom: '5px' }}>
                            <span style={{ color: '#007bff', fontWeight: 'bold', marginRight: '10px' }}>
                                [{item.qualgbnm}]
                            </span>
                            <span style={{ fontSize: '16px', fontWeight: '500' }}>{item.jmfldnm}</span>
                            <span style={{ color: '#6c757d', fontSize: '14px', marginLeft: '10px' }}>
                                ({item.obligfldnm})
                            </span>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}
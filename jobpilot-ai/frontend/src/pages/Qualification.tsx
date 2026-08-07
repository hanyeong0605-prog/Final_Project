/*
한국산업인력공단_국가자격 종목 목록 정보
요청 URL
http://openapi.q-net.or.kr/api/service/rest/InquiryListNationalQualifcationSVC?_wadl&_type=xml

요청값 ?
serviceKey 인증키

필요 반환값
<jmfldnm>
<qualgbnm>
<obligfldnm>

*/



import React, { useState, useEffect } from 'react';
import axios from 'axios';

interface Qualification {
    jmfldnm: string | null;
    qualgbnm: string | null;
    obligfldnm: string | null;
}

export default function Qualification() {
    const [qualifications, setQualifications] = useState<Qualification[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        async function fetchQualifications() {
            const serviceKey = import.meta.env.VITE_DATA_GO_KEY;
            // 수정 전 (이전 코드에 encodeURIComponent가 있거나 axios가 자동 처리할 수 있음)
// const url = `/api/qnet/qualifications?serviceKey=${encodeURIComponent(serviceKey)}`;

// 수정 후: serviceKey를 그대로 전달
//const url = `/api/qnet/qualifications?serviceKey=${serviceKey}`;
//const url = `/api/qnet?serviceKey=${serviceKey}`;
const url = `/openapi?serviceKey=${serviceKey}`;
            try {
                const response = await axios.get(url, {
                    responseType: 'text'
                });

                const parser = new DOMParser();
                const xmlDoc = parser.parseFromString(response.data, "text/xml");

                const itemNodes = xmlDoc.getElementsByTagName("item");
                const parsedData: Qualification[] = [];

                for (let i = 0; i < itemNodes.length; i++) {
                    const item = itemNodes[i];
                    parsedData.push({
                        jmfldnm: item.getElementsByTagName("jmfldnm")[0]?.textContent || null,
                        qualgbnm: item.getElementsByTagName("qualgbnm")[0]?.textContent || null,
                        obligfldnm: item.getElementsByTagName("obligfldnm")[0]?.textContent || null
                    });
                }

                setQualifications(parsedData);
            } catch (error) {
                console.error("API 요청 실패:", error);
            } finally {
                setLoading(false);
            }
        }

        fetchQualifications();
    }, []);

    return (
        <div style={{ padding: '20px' }}>
            <h1>국가자격 종목 목록</h1>
            {loading ? (
                <p>데이터를 불러오는 중입니다...</p>
            ) : (
                <ul>
                    {qualifications.map((item, index) => (
                        <li key={index} style={{ marginBottom: '10px' }}>
                            <strong>[{item.qualgbnm}]</strong> {item.jmfldnm} ({item.obligfldnm})
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}
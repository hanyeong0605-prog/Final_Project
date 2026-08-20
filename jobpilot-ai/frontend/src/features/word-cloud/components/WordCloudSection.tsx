import React, { useState, useEffect } from 'react';
import { getWordCloud, WordCloudResponse } from '../api/WordCloud';
import { WordCloudViewer } from './WordCloudViewer';

type ImportanceType = 'all' | 'required' | 'preferred';

export const WordCloudSection: React.FC<{ showHeader?: boolean; compact?: boolean }> = ({ showHeader = true, compact = false }) => {
  const [importance, setImportance] = useState<ImportanceType>('all');
  const [data, setData] = useState<WordCloudResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(false);

  useEffect(() => {
    let isMounted = true;
    setLoading(true);

    getWordCloud(importance)
      .then((res) => {
        if (isMounted) setData(res);
      })
      .catch((err) => console.error(err))
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [importance]);

  return (
    <div
      style={{
        maxWidth: '800px',
        margin: '0 auto',
        padding: compact ? '8px 0 0' : '16px 0',
        fontFamily:
          '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
      }}
    >
      {showHeader && <div style={{ textAlign: 'center', marginBottom: '28px' }}>
        <p
          style={{
            fontSize: '11px',
            fontWeight: 600,
            color: '#64748b',
            letterSpacing: '0.1em',
            textTransform: 'uppercase',
            marginBottom: '6px',
          }}
        >
          Market Intelligence
        </p>
        <h2
          style={{
            fontSize: '22px',
            fontWeight: 700,
            color: '#0f172a',
            margin: '0 0 8px 0',
            letterSpacing: '-0.02em',
          }}
        >
          기술 스택 키워드 트렌드
        </h2>
        <p style={{ fontSize: '13px', color: '#64748b', margin: 0 }}>
          {importance === 'all' && '수집된 전체 채용 공고 기반의 핵심 기술 키워드 분포입니다.'}
          {importance === 'required' && '채용 공고에서 필수 자격요건으로 지정된 스택 키워드입니다.'}
          {importance === 'preferred' && '우대사항 항목에 등재된 차별화 기술 스택 키워드입니다.'}
        </p>
      </div>}

      {/* Segmented Control Filter Tabs */}
      <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '20px' }}>
        <div
          style={{
            display: 'inline-flex',
            padding: '3px',
            backgroundColor: '#f1f5f9',
            borderRadius: '10px',
            border: '1px solid #e2e8f0',
          }}
        >
          {[
            { key: 'all', label: '전체 키워드' },
            { key: 'required', label: '필수 자격요건' },
            { key: 'preferred', label: '우대사항' },
          ].map((tab) => {
            const isActive = importance === tab.key;
            return (
              <button
                key={tab.key}
                onClick={() => setImportance(tab.key as ImportanceType)}
                style={{
                  padding: '7px 18px',
                  fontSize: '12px',
                  fontWeight: isActive ? 600 : 500,
                  color: isActive ? '#0f172a' : '#64748b',
                  backgroundColor: isActive ? '#ffffff' : 'transparent',
                  border: 'none',
                  borderRadius: '7px',
                  cursor: 'pointer',
                  boxShadow: isActive
                    ? '0 1px 2px rgba(0, 0, 0, 0.06), 0 1px 1px rgba(0, 0, 0, 0.04)'
                    : 'none',
                  transition: 'all 0.15s ease-in-out',
                }}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Counter Badge */}
      {data && (
        <div style={{ textAlign: 'center', marginBottom: '24px' }}>
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '3px 10px',
              borderRadius: '9999px',
              backgroundColor: '#f8fafc',
              border: '1px solid #e2e8f0',
              fontSize: '11px',
              color: '#475569',
              fontWeight: 500,
            }}
          >
            <span
              style={{
                width: '5px',
                height: '5px',
                borderRadius: '50%',
                backgroundColor: '#10b981',
              }}
            />
            {data.total_records.toLocaleString()}건 분석 완료
          </span>
        </div>
      )}

      
      <WordCloudViewer imageData={data?.image_data || null} loading={loading} compact={compact} topKeywords={data?.top_keywords ?? []} />
    </div>
  );
};

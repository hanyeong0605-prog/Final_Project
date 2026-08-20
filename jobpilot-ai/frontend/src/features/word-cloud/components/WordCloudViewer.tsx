
import React from 'react';

interface WordCloudViewerProps {
  imageData: string | null;
  loading: boolean;
  compact?: boolean;
  topKeywords?: Array<{ rank: number; keyword: string; mention_count: number }>;
}

export const WordCloudViewer: React.FC<WordCloudViewerProps> = ({ imageData, loading, compact = false, topKeywords = [] }) => {
  return (
    <div
      style={{
        width: compact ? 'min(100%, 520px)' : 'min(100%, 680px)',
        height: compact ? 'min(100vw - 40px, 520px)' : 'min(100vw - 40px, 680px)',
        margin: '0 auto',
        borderRadius: '20px', // ⭐ '50%'(동그라미) 대신 부드러운 카드형 모서리로 변경
        backgroundColor: 'transparent',
        border: 'none',
        boxShadow: 'none',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        position: 'relative',
        padding: compact ? '0' : '8px',
        overflow: 'visible',
      }}
    >
      {loading ? (
        <div style={{ textAlign: 'center' }}>
          <div
            style={{
              width: '28px',
              height: '28px',
              border: '2.5px solid #e2e8f0',
              borderTopColor: '#4f46e5',
              borderRadius: '50%',
              margin: '0 auto 10px',
              animation: 'spin 0.75s linear infinite',
            }}
          />
          <span style={{ fontSize: '12px', color: '#94a3b8', fontWeight: 500 }}>
            TF-IDF 가중치 연산 중...
          </span>
          <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
        </div>
      ) : imageData ? (
        <>
          <img
            src={imageData}
            alt="Mascot Skill Trend WordCloud"
            style={{
              width: '100%', height: '100%', objectFit: 'contain', transition: 'transform 0.25s ease-out', cursor: 'default',
            }}
            onMouseEnter={(e) => { e.currentTarget.style.transform = 'scale(1.02)'; }}
            onMouseLeave={(e) => { e.currentTarget.style.transform = 'scale(1)'; }}
          />
          {topKeywords.length > 0 && (
            <aside aria-label="상위 5개 기술 키워드" style={{ position: 'absolute', top: compact ? 30 : 50, right: compact ? -8 : 0, width: compact ? 142 : 172, border: '1px solid #dfe4ff', borderRadius: 10, background: 'rgba(255,255,255,.94)', boxShadow: '0 8px 20px rgba(56, 72, 150, .10)', padding: '8px 9px', textAlign: 'left', backdropFilter: 'blur(8px)' }}>
              <strong style={{ display: 'block', color: '#4f46e5', fontSize: 9, letterSpacing: '.07em', marginBottom: 5 }}>TOP 5 기술 키워드</strong>
              <ol style={{ display: 'grid', gap: 3, margin: 0, padding: 0, listStyle: 'none' }}>
                {topKeywords.map((item) => <li key={item.keyword} style={{ display: 'grid', gridTemplateColumns: '14px minmax(0, 1fr) auto', alignItems: 'center', gap: 3, color: '#43506a', fontSize: 9 }}><b style={{ color: '#6979de' }}>{item.rank}</b><span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontWeight: 700 }}>{item.keyword}</span><small style={{ color: '#8a96ad', fontSize: 8 }}>{item.mention_count}건</small></li>)}
              </ol>
            </aside>
          )}
        </>
      ) : (
        <span style={{ fontSize: '12px', color: '#94a3b8' }}>
          데이터를 불러올 수 없습니다.
        </span>
      )}
    </div>
  );
};

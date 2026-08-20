
import React from 'react';

interface WordCloudViewerProps {
  imageData: string | null;
  loading: boolean;
  compact?: boolean;
}

export const WordCloudViewer: React.FC<WordCloudViewerProps> = ({ imageData, loading, compact = false }) => {
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
        overflow: 'hidden',
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
        <img
          src={imageData}
          alt="Mascot Skill Trend WordCloud"
          style={{
            width: '100%',
            height: '100%',
            objectFit: 'contain', // ⭐ 'cover' 대신 'contain'으로 고양이 비율 유지
            transition: 'transform 0.25s ease-out',
            cursor: 'default',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.transform = 'scale(1.02)';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.transform = 'scale(1)';
          }}
        />
      ) : (
        <span style={{ fontSize: '12px', color: '#94a3b8' }}>
          데이터를 불러올 수 없습니다.
        </span>
      )}
    </div>
  );
};

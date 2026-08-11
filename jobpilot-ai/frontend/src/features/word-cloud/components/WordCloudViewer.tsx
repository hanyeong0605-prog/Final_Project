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
        width: compact ? 'min(100%, 360px)' : '460px',
        height: compact ? 'min(100vw - 92px, 360px)' : '460px',
        margin: '0 auto',
        borderRadius: '50%',
        backgroundColor: '#ffffff',
        border: '1px solid #e2e8f0',
        boxShadow: '0 10px 25px -5px rgba(15, 23, 42, 0.04), 0 8px 10px -6px rgba(15, 23, 42, 0.02)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        position: 'relative',
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
          alt="Skill Trend WordCloud"
          style={{
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            borderRadius: '50%',
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

import React, { useState } from 'react';

// 일단 프론트 먼저

// 로그인 안내 모달
function LoginPromptModal({ onLogin, onClose }) {
  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1000,
        background: 'rgba(0,0,0,0.45)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 20,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'white',
          borderRadius: 20,
          padding: '32px 28px',
          maxWidth: 360,
          width: '100%',
          textAlign: 'center',
          boxShadow: '0 20px 60px rgba(0,0,0,0.25)',
        }}
      >
        <div style={{ fontSize: 40, marginBottom: 14 }}>
          🔒
        </div>

        <div
          style={{
            fontSize: 18,
            fontWeight: 800,
            color: '#111827',
            marginBottom: 8,
          }}
        >
          로그인이 필요해요
        </div>

        <div
          style={{
            fontSize: 14,
            color: '#6B7280',
            lineHeight: 1.5,
            marginBottom: 24,
          }}
        >
          게시글은 회원만 이용할 수 있어요.
          <br />
          로그인하고 커뮤니티를 즐겨보세요!
        </div>

        <div style={{ display: 'flex', gap: 8 }}>
          <button
            onClick={onClose}
            style={{
              flex: 1,
              padding: '12px',
              borderRadius: 12,
              border: '1.5px solid #E5E7EB',
              background: 'white',
              color: '#6B7280',
              fontSize: 14,
              fontWeight: 700,
              cursor: 'pointer',
            }}
          >
            다음에
          </button>

          <button
            onClick={onLogin}
            style={{
              flex: 2,
              padding: '12px',
              borderRadius: 12,
              border: 'none',
              background: '#81A6C6',
              color: 'white',
              fontSize: 14,
              fontWeight: 700,
              cursor: 'pointer',
            }}
          >
            로그인하러 가기 →
          </button>
        </div>
      </div>
    </div>
  );
}


// 자유게시판 ( 하드 코딩 )
export default function FreeBoard() {
    
  const [boards, setBoards] = useState([
    {
      id: 1,
      title: '취업 준비하면서 궁금한 점이 있어요',
      author: '홍길동',
      createdAt: '2026.08.13',
      viewCount: 24,
    },
    {
      id: 2,
      title: '요즘 다들 어떤 공부하고 계신가요?',
      author: '김개발',
      createdAt: '2026.08.12',
      viewCount: 18,
    },
    {
      id: 3,
      title: '오늘 면접 보고 왔습니다',
      author: '취준생',
      createdAt: '2026.08.12',
      viewCount: 31,
    },
    {
      id: 4,
      title: '회사 생활 관련해서 질문드립니다',
      author: '직장인',
      createdAt: '2026.08.11',
      viewCount: 15,
    },
    {
      id: 5,
      title: '다들 주말에 뭐 하시나요?',
      author: '개발자',
      createdAt: '2026.08.10',
      viewCount: 9,
    },
  ]);

  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [showLoginModal, setShowLoginModal] = useState(false);

  const isLoggedIn = () => {
    try {
      return !!JSON.parse(localStorage.getItem('member'));
    } catch {
      return false;
    }
  };

const handlePostClick = (boardId) => {
  console.log('게시글:', boardId);
};

  const handleWriteClick = () => {
    if (!isLoggedIn()) {
      setShowLoginModal(true);
    } else {
      console.log('글쓰기');
      
    }
  };

  // 검색
  const filteredBoards = boards.filter((board) =>
    board.title.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div
      style={{
        maxWidth: 1100,
        margin: '0 auto',
        padding: '40px 24px',
      }}
    >
      {/* 게시판 제목 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 28,
        }}
      >
        <div>
          <h1
            style={{
              margin: 0,
              fontSize: 28,
              fontWeight: 800,
              color: '#111827',
            }}
          >
            자유게시판
          </h1>

          <p
            style={{
              marginTop: 8,
              color: '#6B7280',
              fontSize: 14,
            }}
          >
            자유롭게 이야기를 나눠보세요.
          </p>
        </div>

        <button
          onClick={handleWriteClick}
          style={{
            padding: '11px 18px',
            border: 'none',
            borderRadius: 10,
            background: '#81A6C6',
            color: 'white',
            fontSize: 14,
            fontWeight: 700,
            cursor: 'pointer',
          }}
        >
          글쓰기
        </button>
      </div>


      {/* 검색 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          marginBottom: 20,
        }}
      >
        <div
          style={{
            display: 'flex',
            width: 420,
          }}
        >
          <input
            type="text"
            placeholder="게시글 제목을 검색해보세요."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              flex: 1,
              padding: '12px 14px',
              border: '1px solid #D1D5DB',
              borderRadius: '10px 0 0 10px',
              outline: 'none',
              fontSize: 14,
            }}
          />

          <button
            style={{
              width: 70,
              border: 'none',
              borderRadius: '0 10px 10px 0',
              background: '#374151',
              color: 'white',
              fontWeight: 700,
              cursor: 'pointer',
            }}
          >
            검색
          </button>
        </div>
      </div>


      {/* 게시글 목록 */}
      <div
        style={{
          borderTop: '2px solid #111827',
        }}
      >
        {/* 테이블 헤더 */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '80px 1fr 120px 120px 70px',
            padding: '14px 10px',
            borderBottom: '1px solid #E5E7EB',
            background: '#F9FAFB',
            fontSize: 13,
            fontWeight: 700,
            color: '#374151',
            textAlign: 'center',
          }}
        >
          <div>번호</div>
          <div>제목</div>
          <div>작성자</div>
          <div>작성일</div>
          <div>조회</div>
        </div>


        {/* 게시글 */}
        {filteredBoards.length > 0 ? (
          filteredBoards.map((board) => (
            <div
              key={board.id}
              onClick={() => handlePostClick(board.id)}
              style={{
                display: 'grid',
                gridTemplateColumns: '80px 1fr 120px 120px 70px',
                padding: '17px 10px',
                borderBottom: '1px solid #E5E7EB',
                fontSize: 14,
                color: '#374151',
                cursor: 'pointer',
                textAlign: 'center',
              }}
            >
              <div>{board.id}</div>

              <div
                style={{
                  textAlign: 'left',
                  fontWeight: 600,
                  color: '#111827',
                }}
              >
                {board.title}
              </div>

              <div>{board.author}</div>

              <div>{board.createdAt}</div>

              <div>{board.viewCount}</div>
            </div>
          ))
        ) : (
          <div
            style={{
              padding: '60px 20px',
              textAlign: 'center',
              color: '#9CA3AF',
            }}
          >
            검색 결과가 없습니다.
          </div>
        )}
      </div>


      {/* 페이지네이션 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          gap: 8,
          marginTop: 30,
        }}
      >
        <button
          onClick={() => setCurrentPage(Math.max(1, currentPage - 1))}
          style={{
            padding: '8px 12px',
            border: '1px solid #E5E7EB',
            background: 'white',
            borderRadius: 8,
            cursor: 'pointer',
          }}
        >
          ‹
        </button>

        {[1, 2, 3, 4, 5].map((page) => (
          <button
            key={page}
            onClick={() => setCurrentPage(page)}
            style={{
              padding: '8px 12px',
              border: '1px solid #E5E7EB',
              background: currentPage === page ? '#81A6C6' : 'white',
              color: currentPage === page ? 'white' : '#374151',
              borderRadius: 8,
              cursor: 'pointer',
            }}
          >
            {page}
          </button>
        ))}

        <button
          onClick={() => setCurrentPage(currentPage + 1)}
          style={{
            padding: '8px 12px',
            border: '1px solid #E5E7EB',
            background: 'white',
            borderRadius: 8,
            cursor: 'pointer',
          }}
        >
          ›
        </button>
      </div>


      {/* 로그인 모달 */}
      {showLoginModal && (
        <LoginPromptModal
          onLogin={() => {}}
          onClose={() => setShowLoginModal(false)}
        />
      )}
    </div>
  );
}
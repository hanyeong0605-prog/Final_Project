import React, { useState } from 'react';

export default function FreeBoardDetail() {
  return (
    <div
      style={{
        maxWidth: 900,
        margin: '0 auto',
        padding: '40px 24px',
      }}
    >
      {/* 게시판 이름 */}
      <div
        style={{
          fontSize: 14,
          color: '#81A6C6',
          fontWeight: 700,
          marginBottom: 12,
        }}
      >
        자유게시판
      </div>

      {/* 제목 */}
      <h1
        style={{
          margin: 0,
          fontSize: 28,
          fontWeight: 800,
          color: '#111827',
        }}
      >
        취업 준비하면서 궁금한 점이 있어요
      </h1>

      {/* 작성 정보 */}
      <div
        style={{
          display: 'flex',
          gap: 16,
          marginTop: 14,
          paddingBottom: 20,
          borderBottom: '1px solid #E5E7EB',
          fontSize: 13,
          color: '#6B7280',
        }}
      >
        <span>홍길동</span>
        <span>2026.08.13</span>
        <span>조회 24</span>
      </div>

      {/* 본문 */}
      <div
        style={{
          minHeight: 300,
          padding: '30px 10px',
          fontSize: 15,
          lineHeight: 1.8,
          color: '#374151',
          borderBottom: '1px solid #E5E7EB',
        }}
      >
        안녕하세요.
        <br />
        취업 준비하면서 궁금한 점이 있어서 글을 작성합니다.
        <br />
        다들 어떻게 준비하고 계신가요?
      </div>

      {/* 하단 버튼 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          marginTop: 20,
        }}
      >
        <button
          style={{
            padding: '10px 18px',
            border: '1px solid #D1D5DB',
            borderRadius: 8,
            background: 'white',
            cursor: 'pointer',
          }}
        >
          목록
        </button>

        <div style={{ display: 'flex', gap: 8 }}>
          <button
            style={{
              padding: '10px 18px',
              border: '1px solid #D1D5DB',
              borderRadius: 8,
              background: 'white',
              cursor: 'pointer',
            }}
          >
            수정
          </button>

          <button
            style={{
              padding: '10px 18px',
              border: 'none',
              borderRadius: 8,
              background: '#EF4444',
              color: 'white',
              cursor: 'pointer',
            }}
          >
            삭제
          </button>
        </div>
      </div>
    </div>
  );
}
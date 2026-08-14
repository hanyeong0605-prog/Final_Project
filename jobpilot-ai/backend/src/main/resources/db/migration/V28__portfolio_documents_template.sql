-- 포트폴리오 생성 기능에 템플릿(디자인 테마) 선택을 추가한다 - 사용자가 만들기 전에
-- 라이트/다크/브랜드 블루 중 골라서 PptxRenderer/PdfRenderer가 그 색상으로 렌더링한다.
ALTER TABLE portfolio_documents
    ADD COLUMN template VARCHAR(20) NOT NULL DEFAULT 'LIGHT' AFTER narrative_source;

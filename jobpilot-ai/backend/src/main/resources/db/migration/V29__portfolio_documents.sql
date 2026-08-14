-- 포트폴리오 생성 기능 - GitHub 코드 분석 미리보기에서 사용자가 고른 구현 설명을 근거로
-- Gemini(또는 정적 폴백)가 만든 발표 슬라이드 구조(narrative_json)와 렌더링된 pptx/pdf
-- 파일을 저장한다. 이 배포 컨테이너(docker-compose.prod.yml)의 backend 서비스에는 별도
-- volume이 없어 디스크에 저장하면 재배포마다 사라지므로, 파일을 LONGBLOB으로 DB에 직접
-- 저장한다 - 인프라 변경(볼륨 마운트, S3 등) 없이 기존 DB 백업 정책에 자동으로 포함된다.
-- interview_session_records와 같은 이유로 "그때 만든 결과물"이라 수정 없이 생성만 있다.
CREATE TABLE portfolio_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    repository_full_name VARCHAR(255) NOT NULL,
    repository_url VARCHAR(500) NOT NULL,
    title VARCHAR(255) NOT NULL,
    narrative_json JSON NOT NULL,
    source_analysis_snapshot JSON NOT NULL,
    pptx_data LONGBLOB NULL,
    pdf_data LONGBLOB NULL,
    narrative_source VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_portfolio_documents_member (member_id, created_at),
    CONSTRAINT fk_portfolio_documents_member FOREIGN KEY (member_id) REFERENCES members (id)
);

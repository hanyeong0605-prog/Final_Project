-- 실제 크롤링 공고를 이름으로 가상기업과 자동 연결하지 않는다.
-- 명시적으로 등록한 가상기업/공고에만 리뷰를 허용하며 원래 job_postings는 변경하지 않는다.
CREATE TABLE review_companies (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    seed_key VARCHAR(80) NOT NULL,
    name VARCHAR(255) NOT NULL,
    source_type VARCHAR(30) NOT NULL DEFAULT 'FICTIONAL_DEMO',
    description TEXT NOT NULL,
    industry VARCHAR(255) NULL,
    location VARCHAR(255) NULL,
    employer_account_id BIGINT NULL,
    reviews_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_review_company_seed (seed_key),
    CONSTRAINT ck_review_company_fictional CHECK (source_type = 'FICTIONAL_DEMO'),
    CONSTRAINT fk_review_company_employer FOREIGN KEY (employer_account_id) REFERENCES employer_accounts(id)
);

CREATE TABLE review_company_postings (
    job_posting_id BIGINT NOT NULL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    UNIQUE KEY uq_review_posting_company (job_posting_id, company_id),
    KEY ix_review_company_postings (company_id),
    CONSTRAINT fk_review_link_posting FOREIGN KEY (job_posting_id) REFERENCES job_postings(id),
    CONSTRAINT fk_review_link_company FOREIGN KEY (company_id) REFERENCES review_companies(id)
);

CREATE TABLE company_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    job_posting_id BIGINT NULL,
    author_member_id BIGINT NULL,
    seed_key VARCHAR(80) NULL,
    source_type VARCHAR(30) NOT NULL,
    display_author VARCHAR(100) NOT NULL,
    rating INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    pros VARCHAR(1500) NOT NULL,
    cons VARCHAR(1500) NOT NULL,
    body VARCHAR(5000) NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    content_hash CHAR(64) NOT NULL,
    analysis_state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    analysis_attempts INT NOT NULL DEFAULT 0,
    next_analysis_at DATETIME NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 숨김은 중복 작성 권한을 주지 않는다. 삭제된 리뷰만 새 작성이 가능하다.
    active_author_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN visibility <> 'DELETED' THEN author_member_id ELSE NULL END) STORED,
    UNIQUE KEY uq_review_seed (seed_key),
    UNIQUE KEY uq_review_company_author (company_id, active_author_id),
    KEY ix_reviews_company_visible (company_id, visibility, created_at),
    KEY ix_reviews_posting_visible (job_posting_id, visibility, created_at),
    KEY ix_reviews_analysis_queue (analysis_state, next_analysis_at),
    CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_review_visibility CHECK (visibility IN ('PUBLIC', 'HIDDEN', 'DELETED')),
    CONSTRAINT ck_review_analysis CHECK (analysis_state IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_review_provenance CHECK (
        (source_type = 'USER' AND author_member_id IS NOT NULL AND seed_key IS NULL) OR
        (source_type = 'SYNTHETIC_DEMO' AND author_member_id IS NULL AND seed_key IS NOT NULL)),
    CONSTRAINT fk_review_company FOREIGN KEY (company_id) REFERENCES review_companies(id),
    -- 회사 A 리뷰에 회사 B 공고 ID를 지정하는 요청은 DB에서도 거절한다.
    CONSTRAINT fk_review_posting_company FOREIGN KEY (job_posting_id, company_id)
        REFERENCES review_company_postings(job_posting_id, company_id),
    CONSTRAINT fk_review_author FOREIGN KEY (author_member_id) REFERENCES members(id)
);

-- 원문 수정 시 과거 분석은 이력으로 유지하고 현재 content_hash와 일치하는 결과만 노출한다.
-- 커뮤니티 분석은 이 테이블에 넣지 않는다: 회사 TOP 10 집계에 게시판이 섞이지 않도록 한다.
CREATE TABLE company_review_analyses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    model_version VARCHAR(150) NOT NULL,
    policy_version VARCHAR(150) NOT NULL,
    polarity VARCHAR(20) NOT NULL,
    positive_score DOUBLE NOT NULL,
    neutral_score DOUBLE NOT NULL,
    negative_score DOUBLE NOT NULL,
    emotions JSON NOT NULL,
    analyzed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_review_analysis_version (review_id, content_hash, model_version, policy_version),
    CONSTRAINT fk_analysis_review FOREIGN KEY (review_id) REFERENCES company_reviews(id),
    CONSTRAINT ck_analysis_polarity CHECK (polarity IN ('POSITIVE','NEUTRAL','NEGATIVE','MIXED')),
    CONSTRAINT ck_analysis_scores CHECK (positive_score BETWEEN 0 AND 1 AND
        neutral_score BETWEEN 0 AND 1 AND negative_score BETWEEN 0 AND 1)
);

CREATE TABLE company_review_likes (
    review_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (review_id, member_id),
    CONSTRAINT fk_review_like_review FOREIGN KEY (review_id) REFERENCES company_reviews(id),
    CONSTRAINT fk_review_like_member FOREIGN KEY (member_id) REFERENCES members(id)
);

CREATE TABLE company_review_reports (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_review_report_member (review_id, member_id),
    CONSTRAINT ck_review_report_status CHECK (status IN ('OPEN','RESOLVED','DISMISSED')),
    CONSTRAINT fk_review_report_review FOREIGN KEY (review_id) REFERENCES company_reviews(id),
    CONSTRAINT fk_review_report_member FOREIGN KEY (member_id) REFERENCES members(id)
);

CREATE TABLE company_review_moderation_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    admin_member_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_review_moderation_action CHECK (action IN ('HIDE','RESTORE','DELETE')),
    CONSTRAINT fk_review_moderation_review FOREIGN KEY (review_id) REFERENCES company_reviews(id),
    CONSTRAINT fk_review_moderation_admin FOREIGN KEY (admin_member_id) REFERENCES members(id)
);

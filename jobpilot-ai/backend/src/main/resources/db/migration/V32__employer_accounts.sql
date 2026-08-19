-- 2026-08-19: 기업회원(구인 기업) 기능 추가 - "잡코리아처럼 기업회원 가입 시 사업자
-- 진위확인 API를 바로 돌려서 관리자 페이지에 인증완료/확인필요 상태로 보여주고,
-- 관리자가 최종 승인/거절하면 승인된 기업만 채용공고를 직접 등록할 수 있게 해달라"는
-- 요청으로 추가한다.
--
-- Member(role USER/ADMIN)에 role만 늘리는 대신 완전히 별도 테이블/로그인 경로로 뺐다 -
-- 기존 코드 전반의 "role != ADMIN이면 일반 구직자"라는 암묵적 가정(AdminAccessService,
-- 프론트 RequireAdmin 등)을 건드리지 않기 위함이며, 기업 전용 필드(사업자번호, 담당자
-- 정보, 진위확인 결과, 승인 상태)도 Member 엔티티와 성격이 달라 별도 테이블이 자연스럽다.
CREATE TABLE employer_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(80) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    manager_name VARCHAR(80) NOT NULL,
    manager_phone VARCHAR(20) NULL,
    company_name VARCHAR(150) NOT NULL,
    -- 하이픈 제거한 10자리 숫자로 저장한다 (예: 1234567890).
    business_registration_number VARCHAR(12) NOT NULL,
    representative_name VARCHAR(80) NOT NULL,
    -- 국세청 진위확인 API가 요구하는 개업일자 형식 그대로(YYYYMMDD) 저장한다.
    opening_date VARCHAR(8) NOT NULL,
    company_address VARCHAR(255) NULL,
    -- 가입 신청 시점에 국세청 사업자등록정보 진위확인 API를 즉시 호출한 결과.
    -- 이 값은 참고용 표시일 뿐이고, 실제 계정 활성화(로그인 후 기능 사용)는 status가
    -- APPROVED일 때만 가능하다 - 진위확인이 통과해도 관리자가 최종 거절할 수 있다.
    nts_verified BOOLEAN NOT NULL DEFAULT FALSE,
    nts_checked_at DATETIME NULL,
    nts_raw_response TEXT NULL,
    -- PENDING(심사 대기) | APPROVED(승인) | REJECTED(거절)
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(255) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_employer_accounts_login_id (login_id),
    UNIQUE KEY uq_employer_accounts_email (email),
    UNIQUE KEY uq_employer_accounts_business_reg (business_registration_number),
    CONSTRAINT fk_employer_accounts_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES members (id)
);

-- 기업회원이 직접 등록한 공고도 크롤러 공고와 같은 job_postings 테이블/컬럼을 그대로
-- 쓴다("크롤링 한 채용 테이블 컬럼과 동일하게" 요청) - source_provider='EMPLOYER',
-- external_job_id='EMP-{UUID}'로 저장해 기존 (source_provider, external_job_id)
-- 유니크 제약과 크롤러 upsert 경로를 그대로 유지한 채 구분한다.
ALTER TABLE job_postings
    ADD COLUMN employer_account_id BIGINT NULL AFTER source_provider,
    ADD CONSTRAINT fk_job_postings_employer_account FOREIGN KEY (employer_account_id) REFERENCES employer_accounts (id),
    ADD KEY ix_job_postings_employer_account (employer_account_id);

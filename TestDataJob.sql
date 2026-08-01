USE jobpilot;

SET NAMES utf8mb4;

START TRANSACTION;


-- =========================================================
-- 사람인 IT 채용공고 54626733
-- =========================================================

INSERT INTO job_postings (
    external_job_id,
    title,
    company_name,
    company_url,
    description,
    source_url,
    location,
    employment_type,
    experience_type,
    industry_code,
    industry_name,
    job_mid_code,
    job_mid_name,
    job_code,
    job_name,
    salary,
    keywords,
    published_at,
    deadline_at,
    is_rolling_deadline,
    status,
    fetched_at,
    source_updated_at,
    crawl_status,
    crawled_at,
    raw_payload
)
VALUES (
    '54626733',
    'JAVA 개발자 경력직 채용 - 분당',
    '(주)포네트',
    'https://www.saramin.co.kr/zf_user/company-info/view?csn=1448110511&utm_source=job-search-api&utm_medium=api&utm_campaign=saramin-job-search-api',
    'JAVA 개발자 경력직 채용 공고. 경력 10~20년, 대학 졸업(2·3년제) 이상을 요구하며 백엔드·서버개발, 프론트엔드, Java, JSP, React, Spring 관련 직무입니다.',
    'https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=54626733&utm_source=job-search-api&utm_medium=api&utm_campaign=saramin-job-search-api',
    '경기 > 성남시, 성남시 분당구, 수원시, 용인시',
    '정규직, 계약직(정규직 전환 가능)',
    '경력 10~20년',
    '301',
    '솔루션·SI·ERP·CRM',
    '2',
    'IT개발·데이터',
    '84,92,235,240,277,291,2232',
    '소프트웨어개발, 백엔드·서버개발, 프론트엔드, Java, JSP, React, Spring, 풀스택',
    '면접 후 결정',
    '소프트웨어개발',
    FROM_UNIXTIME(1785588259),
    FROM_UNIXTIME(1788188399),
    FALSE,
    'ACTIVE',
    NOW(),
    FROM_UNIXTIME(1785588259),
    'NOT_REQUESTED',
    NULL,
    JSON_OBJECT(
        'source', 'SARAMIN_API',
        'id', '54626733',
        'active', 1,
        'postingTimestamp', 1785588259,
        'modificationTimestamp', 1785588259,
        'openingTimestamp', 1785585600,
        'expirationTimestamp', 1788188399,
        'companyCsn', '1448110511',
        'locationCode', '102180,102190,102220,102400',
        'jobTypeCode', '1,10,2',
        'industryCode', '301',
        'jobMidCode', '2',
        'jobCode', '84,92,235,240,277,291,2232',
        'experienceCode', 2,
        'experienceMin', 10,
        'experienceMax', 20,
        'educationCode', '7',
        'salaryCode', '99',
        'closeTypeCode', '1'
    )
)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    company_name = VALUES(company_name),
    company_url = VALUES(company_url),
    description = VALUES(description),
    source_url = VALUES(source_url),
    location = VALUES(location),
    employment_type = VALUES(employment_type),
    experience_type = VALUES(experience_type),
    industry_code = VALUES(industry_code),
    industry_name = VALUES(industry_name),
    job_mid_code = VALUES(job_mid_code),
    job_mid_name = VALUES(job_mid_name),
    job_code = VALUES(job_code),
    job_name = VALUES(job_name),
    salary = VALUES(salary),
    keywords = VALUES(keywords),
    published_at = VALUES(published_at),
    deadline_at = VALUES(deadline_at),
    status = VALUES(status),
    fetched_at = NOW(),
    source_updated_at = VALUES(source_updated_at),
    raw_payload = VALUES(raw_payload);

SET @job_54626733 = (
    SELECT id
    FROM job_postings
    WHERE external_job_id = '54626733'
);


-- API에서 확인된 경력 조건
INSERT INTO job_requirements (
    job_posting_id,
    type,
    content,
    source_excerpt,
    importance,
    extraction_source,
    verification_status
)
SELECT
    @job_54626733,
    'REQUIRED',
    '경력 10년 이상 20년 이하',
    'experience-level code=2, min=10, max=20',
    'HIGH',
    'SARAMIN_API',
    'VERIFIED'
WHERE NOT EXISTS (
    SELECT 1
    FROM job_requirements
    WHERE job_posting_id = @job_54626733
      AND content = '경력 10년 이상 20년 이하'
);


-- API에서 확인된 학력 조건
INSERT INTO job_requirements (
    job_posting_id,
    type,
    content,
    source_excerpt,
    importance,
    extraction_source,
    verification_status
)
SELECT
    @job_54626733,
    'REQUIRED',
    '대학 졸업(2·3년제) 이상',
    'required-education-level code=7',
    'HIGH',
    'SARAMIN_API',
    'VERIFIED'
WHERE NOT EXISTS (
    SELECT 1
    FROM job_requirements
    WHERE job_posting_id = @job_54626733
      AND content = '대학 졸업(2·3년제) 이상'
);


-- API 직무 분류에서 확인된 기술
INSERT INTO job_requirements (
    job_posting_id,
    type,
    content,
    source_excerpt,
    importance,
    extraction_source,
    verification_status
)
SELECT
    @job_54626733,
    'REQUIRED',
    'Java, JSP, React, Spring 관련 개발 역량',
    'job-code=84,92,235,240,277,291,2232',
    'HIGH',
    'SARAMIN_API',
    'VERIFIED'
WHERE NOT EXISTS (
    SELECT 1
    FROM job_requirements
    WHERE job_posting_id = @job_54626733
      AND content = 'Java, JSP, React, Spring 관련 개발 역량'
);


-- =========================================================
-- 사람인 IT 채용공고 54626518
-- =========================================================

INSERT INTO job_postings (
    external_job_id,
    title,
    company_name,
    company_url,
    description,
    source_url,
    location,
    employment_type,
    experience_type,
    industry_code,
    industry_name,
    job_mid_code,
    job_mid_name,
    job_code,
    job_name,
    salary,
    keywords,
    published_at,
    deadline_at,
    is_rolling_deadline,
    status,
    fetched_at,
    source_updated_at,
    crawl_status,
    crawled_at,
    raw_payload
)
VALUES (
    '54626518',
    '[ICT] 정보보안 모의침투 전문가',
    '(주)베스트에치알 (Best HR)',
    'https://www.saramin.co.kr/zf_user/company-info/view?csn=2648123800&utm_source=job-search-api&utm_medium=api&utm_campaign=saramin-job-search-api',
    '정보보안 모의침투 전문가 채용 공고. 경력 1년 이상, 대학 졸업(2·3년제) 이상을 요구합니다.',
    'https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=54626518&utm_source=job-search-api&utm_medium=api&utm_campaign=saramin-job-search-api',
    '서울 > 서울 전체',
    '정규직',
    '경력 1년 이상',
    '111',
    '시설관리·경비·용역',
    '2',
    'IT개발·데이터',
    '111',
    '헤드헌팅, 모의해킹',
    '면접 후 결정',
    '헤드헌팅, 모의해킹',
    FROM_UNIXTIME(1785587094),
    FROM_UNIXTIME(1786892399),
    FALSE,
    'ACTIVE',
    NOW(),
    FROM_UNIXTIME(1785587094),
    'NOT_REQUESTED',
    NULL,
    JSON_OBJECT(
        'source', 'SARAMIN_API',
        'id', '54626518',
        'active', 1,
        'postingTimestamp', 1785587094,
        'modificationTimestamp', 1785587094,
        'openingTimestamp', 1785585600,
        'expirationTimestamp', 1786892399,
        'companyCsn', '2648123800',
        'locationCode', '101000',
        'jobTypeCode', '1',
        'industryCode', '111',
        'jobMidCode', '2',
        'jobCode', '111',
        'experienceCode', 2,
        'experienceMin', 1,
        'experienceMax', 0,
        'educationCode', '7',
        'salaryCode', '99',
        'closeTypeCode', '1'
    )
)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    company_name = VALUES(company_name),
    company_url = VALUES(company_url),
    description = VALUES(description),
    source_url = VALUES(source_url),
    location = VALUES(location),
    employment_type = VALUES(employment_type),
    experience_type = VALUES(experience_type),
    industry_code = VALUES(industry_code),
    industry_name = VALUES(industry_name),
    job_mid_code = VALUES(job_mid_code),
    job_mid_name = VALUES(job_mid_name),
    job_code = VALUES(job_code),
    job_name = VALUES(job_name),
    salary = VALUES(salary),
    keywords = VALUES(keywords),
    published_at = VALUES(published_at),
    deadline_at = VALUES(deadline_at),
    status = VALUES(status),
    fetched_at = NOW(),
    source_updated_at = VALUES(source_updated_at),
    raw_payload = VALUES(raw_payload);

SET @job_54626518 = (
    SELECT id
    FROM job_postings
    WHERE external_job_id = '54626518'
);


INSERT INTO job_requirements (
    job_posting_id,
    type,
    content,
    source_excerpt,
    importance,
    extraction_source,
    verification_status
)
SELECT
    @job_54626518,
    'REQUIRED',
    '정보보안 및 모의침투 관련 역량',
    'job-code=111, 모의해킹',
    'HIGH',
    'SARAMIN_API',
    'VERIFIED'
WHERE NOT EXISTS (
    SELECT 1
    FROM job_requirements
    WHERE job_posting_id = @job_54626518
      AND content = '정보보안 및 모의침투 관련 역량'
);


INSERT INTO job_requirements (
    job_posting_id,
    type,
    content,
    source_excerpt,
    importance,
    extraction_source,
    verification_status
)
SELECT
    @job_54626518,
    'REQUIRED',
    '관련 경력 1년 이상',
    'experience-level code=2, min=1, max=0',
    'HIGH',
    'SARAMIN_API',
    'VERIFIED'
WHERE NOT EXISTS (
    SELECT 1
    FROM job_requirements
    WHERE job_posting_id = @job_54626518
      AND content = '관련 경력 1년 이상'
);


INSERT INTO job_requirements (
    job_posting_id,
    type,
    content,
    source_excerpt,
    importance,
    extraction_source,
    verification_status
)
SELECT
    @job_54626518,
    'REQUIRED',
    '대학 졸업(2·3년제) 이상',
    'required-education-level code=7',
    'HIGH',
    'SARAMIN_API',
    'VERIFIED'
WHERE NOT EXISTS (
    SELECT 1
    FROM job_requirements
    WHERE job_posting_id = @job_54626518
      AND content = '대학 졸업(2·3년제) 이상'
);


COMMIT;


-- =========================================================
-- 입력 결과 확인
-- =========================================================

SELECT
    jp.id,
    jp.external_job_id,
    jp.company_name,
    jp.title,
    jp.job_mid_code,
    jp.job_mid_name,
    jp.experience_type,
    jp.deadline_at,
    jp.status
FROM job_postings jp
WHERE jp.external_job_id IN ('54626733', '54626518')
ORDER BY jp.external_job_id DESC;


SELECT
    jp.external_job_id,
    jr.type,
    jr.content,
    jr.extraction_source,
    jr.verification_status
FROM job_requirements jr
JOIN job_postings jp
    ON jp.id = jr.job_posting_id
WHERE jp.external_job_id IN ('54626733', '54626518')
ORDER BY jp.external_job_id DESC, jr.id;
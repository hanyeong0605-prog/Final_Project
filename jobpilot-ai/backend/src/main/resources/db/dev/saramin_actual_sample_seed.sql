SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO members (login_id,email,password_hash,nickname,created_at,updated_at)
VALUES ('test-user','test@jobpilot.local','TEST_ONLY_NOT_LOGINABLE','테스트 회원',NOW(),NOW())
ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id), nickname=VALUES(nickname), updated_at=NOW();
SET @member_id=LAST_INSERT_ID();

INSERT INTO member_profiles (member_id,target_role,target_job_family,preferred_locations,experience_type)
VALUES (@member_id,'IT 개발자','IT개발·데이터',JSON_ARRAY('서울','경기'),'ENTRY')
ON DUPLICATE KEY UPDATE target_role=VALUES(target_role),target_job_family=VALUES(target_job_family);

INSERT INTO member_specifications (member_id,total_career_months,technical_summary,updated_at)
VALUES (@member_id,0,'Java와 Spring Boot 학습 및 프로젝트 경험',NOW())
ON DUPLICATE KEY UPDATE total_career_months=VALUES(total_career_months),technical_summary=VALUES(technical_summary),updated_at=NOW();

INSERT INTO job_postings (external_job_id,title,company_name,company_url,description,source_url,location,employment_type,experience_type,industry_code,industry_name,job_mid_code,job_mid_name,job_code,job_name,salary,keywords,published_at,deadline_at,is_rolling_deadline,status,fetched_at,source_updated_at,crawl_status,raw_payload)
VALUES ('54626733','JAVA 개발자 경력직 채용 - 분당','(주)포네트','https://www.saramin.co.kr/zf_user/company-info/view?csn=1448110511','경력 10~20년 JAVA 개발자 채용 공고','https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=54626733','경기 > 성남시, 성남시 분당구, 수원시, 용인시','정규직, 계약직','경력 10~20년','301','솔루션·SI·ERP·CRM','2','IT개발·데이터','84,92,235,240,277,291,2232','백엔드·서버개발, 프론트엔드, Java, JSP, React, Spring','면접 후 결정','소프트웨어개발',FROM_UNIXTIME(1785588259),FROM_UNIXTIME(1788188399),FALSE,'ACTIVE',NOW(),FROM_UNIXTIME(1785588259),'NOT_REQUESTED',JSON_OBJECT('source','SARAMIN_API','id','54626733','testSeed',TRUE))
ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id),title=VALUES(title),company_name=VALUES(company_name),deadline_at=VALUES(deadline_at),raw_payload=VALUES(raw_payload);
SET @job1=LAST_INSERT_ID();

INSERT INTO job_postings (external_job_id,title,company_name,company_url,description,source_url,location,employment_type,experience_type,industry_code,industry_name,job_mid_code,job_mid_name,job_code,job_name,salary,keywords,published_at,deadline_at,is_rolling_deadline,status,fetched_at,source_updated_at,crawl_status,raw_payload)
VALUES ('54626518','[ICT] 정보보안 모의침투 전문가','(주)베스트에치알 (Best HR)','https://www.saramin.co.kr/zf_user/company-info/view?csn=2648123800','경력 1년 이상 정보보안 모의침투 전문가 채용 공고','https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=54626518','서울 > 서울 전체','정규직','경력 1년 이상','111','시설관리·경비·용역','2','IT개발·데이터','111','헤드헌팅, 모의해킹','면접 후 결정','헤드헌팅, 모의해킹',FROM_UNIXTIME(1785587094),FROM_UNIXTIME(1786892399),FALSE,'ACTIVE',NOW(),FROM_UNIXTIME(1785587094),'NOT_REQUESTED',JSON_OBJECT('source','SARAMIN_API','id','54626518','testSeed',TRUE))
ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id),title=VALUES(title),company_name=VALUES(company_name),deadline_at=VALUES(deadline_at),raw_payload=VALUES(raw_payload);
SET @job2=LAST_INSERT_ID();

INSERT INTO job_requirements (job_posting_id,type,content,source_excerpt,importance,extraction_source,verification_status)
SELECT @job1,'REQUIRED','경력 10년 이상 20년 이하','experience-level min=10 max=20','HIGH','SARAMIN_API','VERIFIED'
WHERE NOT EXISTS (SELECT 1 FROM job_requirements WHERE job_posting_id=@job1 AND content='경력 10년 이상 20년 이하');
INSERT INTO job_requirements (job_posting_id,type,content,source_excerpt,importance,extraction_source,verification_status)
SELECT @job2,'REQUIRED','정보보안 및 모의침투 관련 역량','job-code=111, 모의해킹','HIGH','SARAMIN_API','VERIFIED'
WHERE NOT EXISTS (SELECT 1 FROM job_requirements WHERE job_posting_id=@job2 AND content='정보보안 및 모의침투 관련 역량');

INSERT INTO job_matches (member_id,job_posting_id,recommendation_level,readiness_score,summary_comment,missing_required_count,ai_model,profile_snapshot,analyzed_at)
VALUES (@member_id,@job1,'DIFFICULT_NOW',25.00,'테스트 회원은 신입이며 공고의 경력 10~20년 조건을 충족하지 못합니다.',1,'TEST_RULE_BASED',JSON_OBJECT('testSeed',TRUE,'totalCareerMonths',0),NOW())
ON DUPLICATE KEY UPDATE recommendation_level=VALUES(recommendation_level),readiness_score=VALUES(readiness_score),summary_comment=VALUES(summary_comment),ai_model=VALUES(ai_model),analyzed_at=NOW();
INSERT INTO job_matches (member_id,job_posting_id,recommendation_level,readiness_score,summary_comment,missing_required_count,ai_model,profile_snapshot,analyzed_at)
VALUES (@member_id,@job2,'DIFFICULT_NOW',20.00,'테스트 회원에게 정보보안 및 모의침투 경험이 확인되지 않습니다.',1,'TEST_RULE_BASED',JSON_OBJECT('testSeed',TRUE,'totalCareerMonths',0),NOW())
ON DUPLICATE KEY UPDATE recommendation_level=VALUES(recommendation_level),readiness_score=VALUES(readiness_score),summary_comment=VALUES(summary_comment),ai_model=VALUES(ai_model),analyzed_at=NOW();

COMMIT;

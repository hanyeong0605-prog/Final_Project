-- 2026-08-10: 개인 타임라인 기능(태스크 #66) - 완료된 모의면접 세션을 처음으로 DB에
-- 남긴다. strengths/improvements/next_steps/questions는 self_introductions/projects와
-- 달리 배열/객체 배열이라 member_profiles.preferred_locations와 같은 JSON 컬럼으로 둔다.
-- 과거 기록이라 updated_at이 없다 - 생성만 되고 이후 수정되지 않는다(엔티티 docstring 참고).
CREATE TABLE interview_session_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    role VARCHAR(50) NULL,
    interview_mode VARCHAR(20) NOT NULL,
    interview_type VARCHAR(30) NULL,
    question_count INT NOT NULL,
    overall_score INT NULL,
    content_score INT NULL,
    delivery_score INT NULL,
    strengths JSON NULL,
    improvements JSON NULL,
    next_steps JSON NULL,
    questions JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_interview_session_records_member (member_id, created_at),
    CONSTRAINT fk_interview_session_records_member FOREIGN KEY (member_id) REFERENCES members (id)
);

-- 2026-08-29: 모의면접 종합 리포트의 비언어 행동 리뷰(ai-server SessionEvaluationReport의
-- nonverbal_feedback)를 타임라인 기록에도 남긴다. 카메라를 안 썼거나 분석 신뢰도가 부족하면
-- ai-server가 null을 주므로 nullable이고, 이미 쌓인 과거 기록도 전부 null로 남는다.
ALTER TABLE interview_session_records
    ADD COLUMN nonverbal_feedback TEXT NULL;

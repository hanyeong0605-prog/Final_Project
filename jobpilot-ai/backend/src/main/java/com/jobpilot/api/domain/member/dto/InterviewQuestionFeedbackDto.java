package com.jobpilot.api.domain.member.dto;

// SessionEvaluationReport.questions(ai-server)의 항목 하나 - question/feedback/model_answer.
// 요청/응답 양쪽에서 같은 모양이라 하나로 공유한다.
//
// 2026-08-26: faceMetrics(프론트 FaceMetrics를 그대로 실어보낸 것)를 추가했다. Spring은
// 이 값을 해석하거나 검증하지 않고 questions(JSON 컬럼)에 그대로 담아 영속화만 한다 -
// 필드 이름이 프론트 faceAnalysis.ts의 FaceMetrics와 정확히 일치해야 Jackson이 매핑한다.
public record InterviewQuestionFeedbackDto(
        String question,
        String feedback,
        String modelAnswer,
        FaceMetricsDto faceMetrics
) {
    public record FaceMetricsDto(
            Integer blinkCount,
            Integer blinkRatePerMin,
            Integer durationSec,
            ExpectedBlinkRangeDto expectedBlinkRange,
            Integer headMovement,
            Integer gazeOffCenterRatio,
            Integer frameCount
    ) {
        public record ExpectedBlinkRangeDto(Integer low, Integer high) {}
    }
}

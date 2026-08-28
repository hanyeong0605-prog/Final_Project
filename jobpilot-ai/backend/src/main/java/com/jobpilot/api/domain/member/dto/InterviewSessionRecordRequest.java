package com.jobpilot.api.domain.member.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

// 2026-08-10: 프론트(SessionReportPanel)가 ai-server evaluate-session 결과를 그대로 이
// 모양으로 옮겨 담아 POST한다 - Spring은 재평가하지 않고 저장만 한다(resume 도메인과 같은
// 원칙: ai-server는 생성, Spring은 영속성).
public record InterviewSessionRecordRequest(
        String role,
        @NotBlank String interviewMode,
        String interviewType,
        @Min(1) int questionCount,
        Integer overallScore,
        Integer contentScore,
        Integer deliveryScore,
        List<String> strengths,
        List<String> improvements,
        List<String> nextSteps,
        List<InterviewQuestionFeedbackDto> questions,
        // 2026-08-29: 비언어 행동 리뷰. 카메라 미사용/신뢰도 부족이면 ai-server가 null을 주고,
        // 이 필드를 모르는 예전 프론트가 보내면 그냥 null로 들어온다(선택 필드).
        String nonverbalFeedback
) {}

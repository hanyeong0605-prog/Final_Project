package com.jobpilot.api.domain.member.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InterviewSessionRecordDetailResponse(
        Long id,
        String role,
        String interviewMode,
        String interviewType,
        int questionCount,
        Integer overallScore,
        Integer contentScore,
        Integer deliveryScore,
        List<String> strengths,
        List<String> improvements,
        List<String> nextSteps,
        List<InterviewQuestionFeedbackDto> questions,
        // 2026-08-29: 저장 당시 카메라 분석이 충분했을 때만 값이 있다 - 과거 기록은 null이다.
        String nonverbalFeedback,
        LocalDateTime createdAt
) {}

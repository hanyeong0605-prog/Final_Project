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
        LocalDateTime createdAt
) {}

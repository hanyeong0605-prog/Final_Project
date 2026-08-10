package com.jobpilot.api.domain.member.dto;

import java.time.LocalDateTime;

// 타임라인 목록용 - 강점/개선점/문항별 피드백까지 넣으면 목록 응답이 무거워지므로 점수/메타만
// 담는다(상세는 InterviewSessionRecordDetailResponse, GET .../{id}).
public record InterviewSessionRecordSummaryResponse(
        Long id,
        String role,
        String interviewMode,
        String interviewType,
        int questionCount,
        Integer overallScore,
        Integer contentScore,
        Integer deliveryScore,
        LocalDateTime createdAt
) {}

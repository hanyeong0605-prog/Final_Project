package com.jobpilot.api.domain.member.dto;

/**
 * Q-Net InquiryTestInformationNTQSVC.getJMList 응답의 회차 1건.
 * 2026-08-11: 종목코드(jmcd)로 그 해 시행 회차별 필기/실기 시험 일정을 보여주는
 * "상세정보" 기능용 - 원본 API는 필드가 훨씬 많지만(응시자격 서류제출일 등),
 * 사용자가 실제로 궁금해할 핵심 일정만 추려서 노출한다.
 */
public record QnetExamRoundResponse(
        String roundName,
        String writtenExamStart,
        String writtenExamEnd,
        String writtenResultDate,
        String practicalExamStart,
        String practicalExamEnd,
        String finalResultStart,
        String finalResultEnd
) {}

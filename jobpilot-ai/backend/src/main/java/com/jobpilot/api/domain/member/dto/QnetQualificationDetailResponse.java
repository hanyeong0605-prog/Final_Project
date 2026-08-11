package com.jobpilot.api.domain.member.dto;

import java.util.List;

/** 자격증 종목 하나("종목코드" jmcd 기준)의 상세정보 - 응시 수수료 + 올해 시행 회차별 일정. */
public record QnetQualificationDetailResponse(
        String code,
        String name,
        String fee,
        List<QnetExamRoundResponse> rounds
) {}

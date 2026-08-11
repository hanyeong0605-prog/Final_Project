package com.jobpilot.api.domain.member.dto;

// 2026-08-11: "성장 기회 추천" 페이지 "전체 자격증 목록" - 분야별 필터 버튼 목록.
// field는 Q-Net obligfldnm(NCS 직무분야 대분류, 예: "정보통신"), count는 카탈로그 내
// 해당 분야 종목 수(버튼에 "정보통신 (58)" 처럼 표시하는 용도).
public record QnetFieldCountResponse(String field, long count) {}

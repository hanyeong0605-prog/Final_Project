package com.jobpilot.api.domain.member.dto;

import java.util.List;

// 2026-08-11: "성장 기회 추천" 페이지의 "전체 자격증 목록" - 검색어 없이 카탈로그를
// 페이지 단위로 훑어보기 위한 응답. hasMore로 프론트가 "더 보기" 버튼 노출 여부를 판단한다.
public record QnetQualificationPageResponse(List<QnetQualificationResponse> items, boolean hasMore) {}

package com.jobpilot.api.domain.book.dto;

import java.util.List;

public record AladinBookPageResponse(
        List<AladinBookResponse> items,
        boolean hasMore,
        int total,
        String recommendationKeyword,
        String evidence
) {}

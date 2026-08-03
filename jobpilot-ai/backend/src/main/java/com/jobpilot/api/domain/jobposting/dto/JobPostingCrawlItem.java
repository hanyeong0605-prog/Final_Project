package com.jobpilot.api.domain.jobposting.dto;

/**
 * ai-server(파이썬 크롤러)가 보내는 공고 1건. 필드명은 파이썬 쪽 snake_case를
 * Jackson 기본 매핑에 맞춰 camelCase로 받는다 (ai-server/app/domain/crawler/backend_client.py 참고).
 */
public record JobPostingCrawlItem(
        String externalId,
        String title,
        String companyName,
        String sourceUrl,
        String career,
        String employmentType,
        String location,
        String deadlineRaw,
        boolean isRollingDeadline,
        String originSite,
        String jobCategory,
        String description,
        String sourceUpdatedAt
) {
}

package com.jobpilot.api.domain.jobposting.dto;

public record JobCrawlRunStartRequest(String sourceCode, String triggerType) {
}

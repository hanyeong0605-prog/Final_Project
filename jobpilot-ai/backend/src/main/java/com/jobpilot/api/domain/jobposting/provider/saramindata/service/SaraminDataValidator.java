package com.jobpilot.api.domain.jobposting.provider.saramindata.service;

import com.jobpilot.api.domain.jobposting.provider.saramindata.dto.SaraminDataResponse.Job;
import java.net.URI;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SaraminDataValidator {
    public Optional<String> invalidReason(Job job) {
        if (job == null) return Optional.of("공고가 null입니다.");
        if (blank(job.id())) return Optional.of("공고 ID가 없습니다.");
        if (job.id().length() > 150) return Optional.of("공고 ID가 저장 한도를 초과합니다.");
        if (job.position() == null || blank(job.position().title())) return Optional.of("공고 제목이 없습니다.");
        if (job.company() == null || job.company().detail() == null || blank(job.company().detail().name())) {
            return Optional.of("회사명이 없습니다.");
        }
        if (!isSaraminUrl(job.url())) return Optional.of("사람인 원문 URL이 올바르지 않습니다.");
        try {
            long opening = parse(job.openingTimestamp());
            long expiration = parse(job.expirationTimestamp());
            if (opening > 0 && expiration > 0 && expiration < opening) {
                return Optional.of("마감일이 공고 시작일보다 빠릅니다.");
            }
        } catch (NumberFormatException exception) {
            return Optional.of("공고 날짜가 Unix timestamp 형식이 아닙니다.");
        }
        return Optional.empty();
    }

    private boolean isSaraminUrl(String value) {
        if (blank(value)) return false;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            boolean http = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
            return http && host != null
                    && (host.equals("saramin.co.kr") || host.endsWith(".saramin.co.kr"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private long parse(String value) { return blank(value) ? 0 : Long.parseLong(value); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}

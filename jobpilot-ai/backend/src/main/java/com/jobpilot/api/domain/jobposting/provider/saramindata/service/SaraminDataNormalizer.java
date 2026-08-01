package com.jobpilot.api.domain.jobposting.provider.saramindata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.jobposting.provider.saramindata.dto.SaraminDataResponse.CodeName;
import com.jobpilot.api.domain.jobposting.provider.saramindata.dto.SaraminDataResponse.Job;
import com.jobpilot.api.domain.jobposting.provider.saramindata.model.NormalizedSaraminPosting;
import com.jobpilot.api.domain.jobposting.provider.saramindata.model.NormalizedSaraminPosting.Requirement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SaraminDataNormalizer {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final ObjectMapper objectMapper;

    public SaraminDataNormalizer(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public NormalizedSaraminPosting normalize(Job job, SaraminDataCrawler.CrawlResult crawl) {
        List<Requirement> requirements = new ArrayList<>();
        addRequirement(requirements, "REQUIRED", name(job.position().experienceLevel()), "HIGH");
        addRequirement(requirements, "REQUIRED", name(job.position().requiredEducationLevel()), "HIGH");
        for (String excerpt : crawl.requirementExcerpts()) {
            String type = excerpt.contains("우대") ? "PREFERRED" : "REQUIRED";
            requirements.add(new Requirement(type, excerpt, excerpt, "MEDIUM", "SARAMIN_CRAWL", "NEEDS_REVIEW"));
        }

        String description = crawl.description().isBlank() ? apiSummary(job) : crawl.description();
        boolean rolling = job.closeType() != null && ("2".equals(job.closeType().code())
                || "3".equals(job.closeType().code()) || "4".equals(job.closeType().code()));

        return new NormalizedSaraminPosting(
                job.id().trim(),
                truncate(job.position().title().trim(), 500),
                truncate(job.company().detail().name().trim(), 255),
                secureOptionalUrl(job.company().detail().href()),
                description,
                secureUrl(job.url()),
                truncate(name(job.position().location()), 255),
                truncate(name(job.position().jobType()), 50),
                truncate(name(job.position().experienceLevel()), 50),
                code(job.position().industry()),
                truncate(name(job.position().industry()), 255),
                code(job.position().jobMidCode()),
                truncate(name(job.position().jobMidCode()), 255),
                code(job.position().jobCode()),
                truncate(name(job.position().jobCode()), 1000),
                truncate(name(job.salary()), 255),
                job.keyword(),
                timestamp(job.postingTimestamp(), job.openingTimestamp()),
                timestamp(job.expirationTimestamp()),
                rolling,
                job.active() == 1 ? "ACTIVE" : "CLOSED",
                timestamp(job.modificationTimestamp()),
                crawl.status(),
                "SUCCESS".equals(crawl.status()) ? LocalDateTime.now() : null,
                objectMapper.valueToTree(job),
                List.copyOf(requirements)
        );
    }

    private String apiSummary(Job job) {
        List<String> parts = new ArrayList<>();
        add(parts, "직무분류", name(job.position().jobMidCode()));
        add(parts, "직무", name(job.position().jobCode()));
        add(parts, "고용형태", name(job.position().jobType()));
        add(parts, "경력", name(job.position().experienceLevel()));
        add(parts, "학력", name(job.position().requiredEducationLevel()));
        add(parts, "급여", name(job.salary()));
        add(parts, "키워드", job.keyword());
        return String.join(" · ", parts);
    }

    private void add(List<String> parts, String label, String value) {
        if (value != null && !value.isBlank()) parts.add(label + ": " + value);
    }

    private void addRequirement(List<Requirement> requirements, String type, String content, String importance) {
        if (content != null && !content.isBlank()) {
            requirements.add(new Requirement(type, content, content, importance, "SARAMIN_API", "VERIFIED"));
        }
    }

    private String name(CodeName value) { return value == null ? null : value.name(); }
    private String code(CodeName value) { return value == null ? null : value.code(); }
    private String name(com.jobpilot.api.domain.jobposting.provider.saramindata.dto.SaraminDataResponse.ExperienceLevel value) {
        return value == null ? null : value.name();
    }

    private LocalDateTime timestamp(String... candidates) {
        for (String value : candidates) {
            if (value != null && !value.isBlank()) {
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(value)), SEOUL);
            }
        }
        return null;
    }

    private String secureUrl(String value) {
        return value.replaceFirst("^http://", "https://");
    }

    private String secureOptionalUrl(String value) {
        return value == null || value.isBlank() ? null : secureUrl(value);
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

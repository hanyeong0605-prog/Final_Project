package com.jobpilot.api.domain.jobposting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.jobposting.dto.JobPostingCrawlBatchRequest;
import com.jobpilot.api.domain.jobposting.dto.JobPostingCrawlItem;
import com.jobpilot.api.domain.jobposting.dto.JobPostingIngestResult;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크롤러(ai-server)가 보낸 공고를 job_postings 테이블에 upsert한다.
 *
 * 2026-08-03 업데이트: 팀장님이 V1__core_schema.sql을 사람인 API 단일 소스 기준으로
 * 다시 짜면서 job_sources 테이블과 job_postings.source_id 컬럼이 아예 없어졌다
 * (원티드만 쓰기로 했으니 오히려 "출처 구분"이 필요 없어져서 더 단순해짐). 그래서
 * source_id 관련 로직을 전부 제거하고 external_job_id 하나만으로 upsert하도록 고쳤다.
 * sourceCode 파라미터는 Python 크롤러 쪽 코드를 그대로 두기 위해 시그니처만 남겨뒀고
 * 실제로는 사용하지 않는다.
 *
 * ingest() 한 번 호출될 때마다 두 가지 부가 작업이 같이 일어난다:
 * 1) source_updated_at(=lastmod 등 변경 감지용 시각) 저장 -> findExistingSourceUpdatedAt()로
 *    다시 내려줘서, 크롤러가 다음번엔 이미 아는 공고의 상세 페이지 요청을 건너뛸 수 있게 한다.
 * 2) 마감일(deadline_at)이 지난 공고는 status를 CLOSED로 내린다 (크롤러가 새 공고를
 *    하나도 못 찾아도 - items가 비어있어도 - 이 정리는 항상 돈다).
 */
@Service
public class JobPostingIngestService {
    private static final Set<String> SUPPORTED_SOURCE_PROVIDERS = Set.of("WANTED", "ZIGHANG", "SARAMIN_DATA");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JobPostingIngestService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobPostingIngestResult ingest(JobPostingCrawlBatchRequest request) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        String sourceProvider = normalizeSourceProvider(request.sourceCode());

        List<JobPostingCrawlItem> items = request.items() == null ? List.of() : request.items();
        for (JobPostingCrawlItem item : items) {
            if (isBlank(item.externalId()) || isBlank(item.title()) || isBlank(item.sourceUrl())) {
                skipped++;
                continue;
            }
            boolean existed = existsByExternalId(sourceProvider, item.externalId());
            upsert(sourceProvider, item);
            if (existed) {
                updated++;
            } else {
                created++;
            }
        }

        closeExpiredPostings();

        return new JobPostingIngestResult(items.size(), created, updated, skipped);
    }

    /** 이미 갖고 있는 공고들의 {external_job_id: source_updated_at(ISO 문자열)} 맵.
     * sourceCode는 지금 스키마에선 안 쓰지만, 크롤러(router.py)가 그대로 호출하고
     * 있어서 파라미터만 유지한다. */
    public Map<String, String> findExistingSourceUpdatedAt(String sourceCode) {
        String sourceProvider = normalizeSourceProvider(sourceCode);
        return jdbcTemplate.query(
                "SELECT external_job_id, source_updated_at FROM job_postings WHERE source_provider = ?",
                rs -> {
                    Map<String, String> map = new HashMap<>();
                    while (rs.next()) {
                        String externalId = rs.getString("external_job_id");
                        Timestamp ts = rs.getTimestamp("source_updated_at");
                        if (ts != null) {
                            map.put(externalId, ts.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                        }
                    }
                    return map;
                },
                sourceProvider
        );
    }

    private boolean existsByExternalId(String sourceProvider, String externalId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_postings WHERE source_provider = ? AND external_job_id = ?",
                Integer.class,
                sourceProvider,
                externalId
        );
        return count != null && count > 0;
    }

    private void upsert(String sourceProvider, JobPostingCrawlItem item) {
        String rawPayload = toJson(item);
        LocalDateTime deadlineAt = parseDeadline(item.deadlineRaw());
        LocalDateTime sourceUpdatedAt = parseFlexibleDateTime(item.sourceUpdatedAt());

        jdbcTemplate.update(
                "INSERT INTO job_postings "
                        + "(external_job_id, source_provider, title, company_name, description, source_url, "
                        + " location, employment_type, experience_type, job_mid_name, deadline_at, is_rolling_deadline, "
                        + " status, fetched_at, source_updated_at, raw_payload) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', NOW(), ?, CAST(? AS JSON)) "
                        + "ON DUPLICATE KEY UPDATE "
                        + " title = VALUES(title), "
                        + " company_name = VALUES(company_name), "
                        + " description = VALUES(description), "
                        + " source_url = VALUES(source_url), "
                        + " location = VALUES(location), "
                        + " employment_type = VALUES(employment_type), "
                        + " experience_type = VALUES(experience_type), "
                        + " job_mid_name = VALUES(job_mid_name), "
                        + " deadline_at = VALUES(deadline_at), "
                        + " is_rolling_deadline = VALUES(is_rolling_deadline), "
                        + " status = 'ACTIVE', "
                        + " fetched_at = NOW(), "
                        + " source_updated_at = VALUES(source_updated_at), "
                        + " raw_payload = VALUES(raw_payload)",
                item.externalId(),
                sourceProvider,
                item.title(),
                item.companyName(),
                item.description(),
                item.sourceUrl(),
                item.location(),
                item.employmentType(),
                item.career(),
                item.jobCategory(),
                deadlineAt,
                item.isRollingDeadline(),
                sourceUpdatedAt,
                rawPayload
        );
    }

    /** 마감일이 지난 공고를 CLOSED로 내린다. 새로 들어온 공고가 없어도(items=[]) 매번 실행된다. */
    private void closeExpiredPostings() {
        jdbcTemplate.update(
                "UPDATE job_postings SET status = 'CLOSED' "
                        + "WHERE deadline_at IS NOT NULL AND deadline_at < NOW() "
                        + "AND status <> 'CLOSED'",
                new Object[0]
        );
    }

    private LocalDateTime parseDeadline(String deadlineRaw) {
        return parseFlexibleDateTime(deadlineRaw);
    }

    /** ISO 오프셋 datetime / 오프셋 없는 datetime / 날짜만(YYYY-MM-DD) 순으로 시도해서 파싱. */
    private LocalDateTime parseFlexibleDateTime(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(raw);
            } catch (DateTimeParseException e2) {
                try {
                    return LocalDate.parse(raw).atStartOfDay();
                } catch (DateTimeParseException e3) {
                    return null;
                }
            }
        }
    }

    private String toJson(JobPostingCrawlItem item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeSourceProvider(String sourceCode) {
        String sourceProvider = isBlank(sourceCode) ? "WANTED" : sourceCode.trim().toUpperCase();
        if (!SUPPORTED_SOURCE_PROVIDERS.contains(sourceProvider)) {
            throw new IllegalArgumentException("Unsupported job posting source: " + sourceProvider);
        }
        return sourceProvider;
    }
}

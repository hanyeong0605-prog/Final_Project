package com.jobpilot.api.domain.jobposting.service;

import com.jobpilot.api.domain.jobposting.dto.JobPostingListResponse;
import com.jobpilot.api.domain.jobposting.dto.JobPostingPageResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Lightweight list query for the job board. It intentionally does not load raw_payload or descriptions. */
@Service
public class JobPostingSearchService {
    private static final int DEFAULT_SIZE = 24;
    private static final int MAX_SIZE = 60;
    private static final String SEARCH_TEXT = "LOWER(CONCAT_WS(' ', "
            + "COALESCE(title, ''), COALESCE(job_name, ''), COALESCE(job_mid_name, ''), "
            + "COALESCE(keywords, ''), COALESCE(description, '')))";
    // 2026-08-26: 직무(role) 필터 전용 - job_postings.*에 SEARCH_TEXT를 딱 한 번 계산해서
    // search_text 컬럼으로 얹어둔 서브쿼리. 역할 키워드 조건(최대 81개 LIKE)이 이 컬럼
    // 하나만 재사용하도록 해서, 행마다 반복 계산되던 비용(특히 description은 MEDIUMTEXT라
    // 길 수 있음)을 없앤다. 역할 필터가 없는 요청(대부분)은 이 서브쿼리를 아예 안 쓴다.
    private static final String SEARCHABLE_JOB_POSTINGS_SUBQUERY =
            "(SELECT job_postings.*, " + SEARCH_TEXT + " AS search_text FROM job_postings) job_postings";

    private static final Map<String, List<String>> ROLE_KEYWORDS = Map.of(
            "BACKEND", List.of("backend", "백엔드", "server", "서버", "spring", "django", "fastapi", "node.js", "nodejs"),
            "FRONTEND", List.of("frontend", "프론트엔드", "react", "vue", "angular"),
            "FULLSTACK", List.of("fullstack", "full stack", "풀스택"),
            "MOBILE", List.of("android", "ios", "flutter", "react native", "모바일"),
            "DATA_AI", List.of("data", "데이터", "machine learning", "딥러닝", "deep learning", "ai ", " ai", "인공지능"),
            "DEVOPS", List.of("devops", "infra", "인프라", "kubernetes", "k8s", "sre", "aws", "cloud"),
            "QA", List.of("qa", "quality assurance", "테스트", "test engineer"),
            "SECURITY", List.of("security", "보안", "information security"),
            "GAME_EMBEDDED", List.of("game", "게임", "embedded", "임베디드", "firmware", "펌웨어")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JobPostingSearchService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public JobPostingPageResponse search(
            String query,
            String roles,
            String experience,
            String location,
            String employmentType,
            String sort,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        String normalizedSort = normalizeSort(sort);
        MapSqlParameterSource parameters = new MapSqlParameterSource("status", "ACTIVE");
        List<String> selectedRoles = parseRoles(roles);
        // 2026-08-26: 직무 필터를 고르면 페이지가 얼어붙는다는 리포트를 받고 확인해보니, 아래
        // SEARCH_TEXT(제목+직무명+키워드+본문 설명을 이어붙여 소문자로 변환)가 역할당
        // 키워드마다(최대 9개), 역할 개수만큼(최대 9개) 반복돼서 - 최악의 경우 행 하나당 이
        // 무거운 계산(특히 description은 MEDIUMTEXT라 길 수 있음)을 81번까지 매번 새로
        // 돌리고 있었다. 게다가 COUNT 쿼리와 본문 SELECT 쿼리 둘 다 이 WHERE를 그대로 써서
        // 전체 스캔이 2번씩 일어난다. 실사용 데이터(3천 건 이상)에서는 이게 그대로 렉으로
        // 체감된다 - 사용자가 그 사이에 필터를 여러 번 누르면 응답이 한꺼번에 몰려 돌아와서
        // "여러 개가 한번에 눌린 것처럼" 보이는 것도 이 지연이 원인으로 보인다.
        // 근본 수정(FULLTEXT 인덱스 등)은 스키마 변경이 필요해 범위가 커서, 우선 SEARCH_TEXT를
        // 서브쿼리에서 행마다 "한 번만" 계산해두고 그 별칭을 재사용하도록 바꿔서 반복 계산
        // 비용만 없앴다 - 역할 필터를 안 쓰면(대부분의 요청) 기존과 동일한 단순 쿼리 그대로다.
        String from = selectedRoles.isEmpty() ? "job_postings" : SEARCHABLE_JOB_POSTINGS_SUBQUERY;
        String where = buildWhere(query, selectedRoles, experience, location, employmentType, parameters);
        long totalElements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + from + where, parameters, Long.class);

        parameters.addValue("limit", safeSize);
        parameters.addValue("offset", safePage * safeSize);
        String sql = "SELECT id, external_job_id, company_name, company_logo_url, "
                + "COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_payload, '$.imageUrls[0]')), 'null'), "
                + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_payload, '$.images.job_thumbnail_urls[0]')), 'null'), company_logo_url) AS thumbnail_url, "
                + "title, source_url, location, employment_type, "
                + "experience_type, job_name, salary, keywords, published_at, deadline_at, "
                + "is_rolling_deadline, status, COALESCE(view_count, 0) AS view_count, "
                + "(SELECT COUNT(*) FROM user_interests interest WHERE interest.target_type = 'JOB_POSTING' AND interest.target_id = job_postings.id) AS bookmark_count, "
                + "EXISTS (SELECT 1 FROM company_dart_matches finance_match "
                + "JOIN company_financial_years financial ON financial.corp_code = finance_match.corp_code "
                + "WHERE finance_match.source_provider = job_postings.source_provider "
                + "AND finance_match.source_company_id = COALESCE(job_postings.source_company_id, '') "
                + "AND finance_match.match_status = 'CONFIRMED' "
                + "AND finance_match.normalized_company_name = LOWER(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(job_postings.company_name, ''), '주식회사', ''), '(주)', ''), '㈜', ''), '[^[:alnum:]가-힣]', '')) "
                + "AND financial.report_code = '11011') AS has_financials "
                + "FROM " + from + where + " ORDER BY " + orderBy(normalizedSort) + " LIMIT :limit OFFSET :offset";
        List<JobPostingListResponse> content = jdbcTemplate.query(sql, parameters, JOB_POSTING_ROW_MAPPER);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        return new JobPostingPageResponse(content, safePage, safeSize, totalElements, totalPages, normalizedSort);
    }

    private String buildWhere(
            String query,
            List<String> selectedRoles,
            String experience,
            String location,
            String employmentType,
            MapSqlParameterSource parameters
    ) {
        StringBuilder where = new StringBuilder(" WHERE status = :status AND (deadline_at IS NULL OR deadline_at >= NOW())");
        if (hasText(query)) {
            where.append(" AND LOWER(CONCAT_WS(' ', COALESCE(title, ''), COALESCE(company_name, ''), "
                    + "COALESCE(location, ''), COALESCE(job_name, ''), COALESCE(job_mid_name, ''), "
                    + "COALESCE(keywords, ''))) LIKE :query");
            parameters.addValue("query", "%" + query.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if ("ENTRY".equalsIgnoreCase(experience)) {
            where.append(" AND is_entry_level = TRUE");
        } else if ("EXPERIENCED".equalsIgnoreCase(experience)) {
            where.append(" AND (is_entry_level = FALSE OR (is_entry_level IS NULL AND experience_type IS NOT NULL))");
        }
        if (hasText(location)) {
            where.append(" AND location LIKE :location");
            parameters.addValue("location", "%" + location.trim() + "%");
        }
        if (hasText(employmentType)) {
            where.append(" AND LOWER(COALESCE(employment_type, '')) = :employmentType");
            parameters.addValue("employmentType", employmentType.trim().toLowerCase(Locale.ROOT));
        }

        if (!selectedRoles.isEmpty()) {
            where.append(" AND (");
            for (int roleIndex = 0; roleIndex < selectedRoles.size(); roleIndex++) {
                if (roleIndex > 0) where.append(" OR ");
                String role = selectedRoles.get(roleIndex);
                List<String> keywords = ROLE_KEYWORDS.get(role);
                where.append("(");
                for (int keywordIndex = 0; keywordIndex < keywords.size(); keywordIndex++) {
                    if (keywordIndex > 0) where.append(" OR ");
                    String parameterName = "role" + roleIndex + "Keyword" + keywordIndex;
                    // 2026-08-26: SEARCH_TEXT를 매번 다시 계산하지 않고, 서브쿼리(위 search()의
                    // SEARCHABLE_JOB_POSTINGS_SUBQUERY)가 미리 계산해둔 search_text 컬럼을 그대로 쓴다.
                    where.append("search_text LIKE :").append(parameterName);
                    parameters.addValue(parameterName, "%" + keywords.get(keywordIndex).toLowerCase(Locale.ROOT) + "%");
                }
                where.append(")");
            }
            where.append(")");
        }
        return where.toString();
    }

    private List<String> parseRoles(String roles) {
        if (!hasText(roles)) return List.of();
        Set<String> result = new LinkedHashSet<>();
        for (String role : roles.split(",")) {
            String normalized = role.trim().toUpperCase(Locale.ROOT);
            if (ROLE_KEYWORDS.containsKey(normalized)) result.add(normalized);
        }
        return List.copyOf(result);
    }

    private String normalizeSort(String sort) {
        if ("deadline_desc".equalsIgnoreCase(sort)) return "deadline_desc";
        if ("recent".equalsIgnoreCase(sort)) return "recent";
        if ("popular".equalsIgnoreCase(sort)) return "popular";
        return "deadline_asc";
    }

    private String orderBy(String sort) {
        return switch (sort) {
            case "deadline_desc" -> "CASE WHEN deadline_at IS NULL THEN 1 ELSE 0 END, deadline_at DESC, id DESC";
            case "recent" -> "fetched_at DESC, id DESC";
            case "popular" -> "(COALESCE(view_count, 0) + ((SELECT COUNT(*) FROM user_interests interest WHERE interest.target_type = 'JOB_POSTING' AND interest.target_id = job_postings.id) * 5)) DESC, COALESCE(view_count, 0) DESC, fetched_at DESC";
            default -> "CASE WHEN deadline_at IS NULL THEN 1 ELSE 0 END, deadline_at ASC, id DESC";
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final RowMapper<JobPostingListResponse> JOB_POSTING_ROW_MAPPER = (resultSet, rowNum) ->
            new JobPostingListResponse(
                    resultSet.getLong("id"), resultSet.getString("external_job_id"), resultSet.getString("company_name"),
                    resultSet.getString("company_logo_url"), resultSet.getString("thumbnail_url"), resultSet.getString("title"), resultSet.getString("source_url"), resultSet.getString("location"),
                    resultSet.getString("employment_type"), resultSet.getString("experience_type"), resultSet.getString("job_name"),
                    resultSet.getString("salary"), resultSet.getString("keywords"), date(resultSet, "published_at"),
                    date(resultSet, "deadline_at"), resultSet.getBoolean("is_rolling_deadline"), resultSet.getString("status"),
                    resultSet.getLong("view_count"), resultSet.getLong("bookmark_count"), resultSet.getBoolean("has_financials"));

    private static LocalDateTime date(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}

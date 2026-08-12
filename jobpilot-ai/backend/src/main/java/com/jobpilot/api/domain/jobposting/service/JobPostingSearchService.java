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
        String where = buildWhere(query, roles, experience, location, employmentType, parameters);
        long totalElements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_postings" + where, parameters, Long.class);

        parameters.addValue("limit", safeSize);
        parameters.addValue("offset", safePage * safeSize);
        String sql = "SELECT id, external_job_id, company_name, company_logo_url, "
                + "COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_payload, '$.imageUrls[0]')), 'null'), "
                + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(raw_payload, '$.images.job_thumbnail_urls[0]')), 'null'), company_logo_url) AS thumbnail_url, "
                + "title, source_url, location, employment_type, "
                + "experience_type, job_name, salary, keywords, published_at, deadline_at, "
                + "is_rolling_deadline, status, COALESCE(view_count, 0) AS view_count, "
                + "(SELECT COUNT(*) FROM user_interests interest WHERE interest.target_type = 'JOB_POSTING' AND interest.target_id = job_postings.id) AS bookmark_count "
                + "FROM job_postings" + where + " ORDER BY " + orderBy(normalizedSort) + " LIMIT :limit OFFSET :offset";
        List<JobPostingListResponse> content = jdbcTemplate.query(sql, parameters, JOB_POSTING_ROW_MAPPER);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        return new JobPostingPageResponse(content, safePage, safeSize, totalElements, totalPages, normalizedSort);
    }

    private String buildWhere(
            String query,
            String roles,
            String experience,
            String location,
            String employmentType,
            MapSqlParameterSource parameters
    ) {
        StringBuilder where = new StringBuilder(" WHERE status = :status");
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

        List<String> selectedRoles = parseRoles(roles);
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
                    where.append(SEARCH_TEXT).append(" LIKE :").append(parameterName);
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
                    resultSet.getLong("view_count"), resultSet.getLong("bookmark_count"));

    private static LocalDateTime date(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}

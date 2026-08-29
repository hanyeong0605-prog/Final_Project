package com.jobpilot.api.domain.review.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 명시적 시연 회사 카탈로그. 실제 회사명 문자열 일치로 자동 병합하지 않는다. */
@Repository
public class ReviewCompanyCatalog {
    private final JdbcTemplate jdbc;
    public ReviewCompanyCatalog(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Company(Long id, String name, String description, String industry, String location,
                          String sourceType, boolean reviewsEnabled) {}

    public Optional<Company> find(Long id) {
        return jdbc.query("SELECT * FROM review_companies WHERE id = ?", (rs, row) ->
                new Company(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                        rs.getString("industry"), rs.getString("location"), rs.getString("source_type"),
                        rs.getBoolean("reviews_enabled")), id).stream().findFirst();
    }

    public List<Company> list(int limit, int offset) {
        return jdbc.query("SELECT * FROM review_companies ORDER BY id LIMIT ? OFFSET ?", (rs, row) ->
                new Company(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                        rs.getString("industry"), rs.getString("location"), rs.getString("source_type"),
                        rs.getBoolean("reviews_enabled")), limit, offset);
    }

    public boolean acceptsPosting(Long companyId, Long postingId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM review_company_postings link
                JOIN job_postings p ON p.id = link.job_posting_id
                WHERE link.company_id = ? AND link.job_posting_id = ?
                  AND p.source_provider = 'FICTIONAL_DEMO' AND p.status = 'ACTIVE'
                """, Integer.class, companyId, postingId);
        return count != null && count == 1;
    }
}

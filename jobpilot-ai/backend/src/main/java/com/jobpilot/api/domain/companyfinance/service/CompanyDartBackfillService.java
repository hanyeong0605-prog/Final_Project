package com.jobpilot.api.domain.companyfinance.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyDartBackfillService {
    private final JdbcTemplate jdbc;
    private final CompanyDartMatchingService matchingService;
    private final CompanyNameNormalizer normalizer;

    public CompanyDartBackfillService(JdbcTemplate jdbc, CompanyDartMatchingService matchingService,
                                      CompanyNameNormalizer normalizer) {
        this.jdbc = jdbc;
        this.matchingService = matchingService;
        this.normalizer = normalizer;
    }

    @Transactional
    public CompanyMatchReport backfillExistingPostings() {
        List<CompanySource> companies = jdbc.query("""
                SELECT source_provider, COALESCE(source_company_id, ''), company_name
                FROM job_postings
                WHERE company_name IS NOT NULL AND TRIM(company_name) <> ''
                GROUP BY source_provider, COALESCE(source_company_id, ''), company_name
                """, (rs, row) -> new CompanySource(rs.getString(1), rs.getString(2), rs.getString(3)));
        List<DartCorporationCandidate> corporations = jdbc.query(
                "SELECT corp_code, corp_name FROM dart_corporations", (rs, row) -> new DartCorporationCandidate(rs.getString(1), rs.getString(2)));
        List<CompanyMatchStatus> statuses = new ArrayList<>();
        for (CompanySource company : companies) {
            CompanyMatchDecision decision = matchingService.match(company.companyName(), corporations);
            statuses.add(decision.status());
            jdbc.update("""
                    INSERT INTO company_dart_matches (source_provider, source_company_id, normalized_company_name, corp_code, match_status, match_method, confidence, verified_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CASE WHEN ? = 'CONFIRMED' THEN NOW() ELSE NULL END)
                    ON DUPLICATE KEY UPDATE corp_code = VALUES(corp_code), match_status = VALUES(match_status),
                        match_method = VALUES(match_method), confidence = VALUES(confidence), verified_at = VALUES(verified_at)
                    """, company.sourceProvider(), company.sourceCompanyId(), normalizer.normalize(company.companyName()),
                    decision.corpCode(), decision.status().name(), decision.status() == CompanyMatchStatus.CONFIRMED ? "NORMALIZED_EXACT" : "NAME_CANDIDATE",
                    decision.status() == CompanyMatchStatus.CONFIRMED ? 1.0 : 0.0, decision.status().name());
        }
        return CompanyMatchReport.from(statuses);
    }
}

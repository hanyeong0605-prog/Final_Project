package com.jobpilot.api.domain.companyfinance.service;

import com.jobpilot.api.domain.companyfinance.dto.CompanyFinanceAnalysisResponse;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyFinanceAnalysisService {
    private static final String MATCH_SQL = """
            SELECT corp_code, match_status
            FROM company_dart_matches
            WHERE source_provider = ? AND source_company_id = ? AND normalized_company_name = ?
            LIMIT 1
            """;
    private static final String FINANCIALS_SQL = """
            SELECT business_year, revenue, operating_income, net_income, total_assets, total_liabilities,
                   total_equity, operating_cash_flow, fs_div, rcept_no
            FROM company_financial_years
            WHERE corp_code = ? AND report_code = '11011'
            ORDER BY business_year ASC
            """;
    private static final String FORECAST_SQL = """
            SELECT base_year, outlook, confidence, growth_probability,
                   profitability_improvement_probability, stability_risk_probability,
                   model_version, evidence, generated_at
            FROM company_growth_predictions
            WHERE corp_code = ?
            ORDER BY generated_at DESC, id DESC
            LIMIT 1
            """;

    private final JobPostingRepository postings;
    private final JdbcTemplate jdbc;
    private final CompanyNameNormalizer normalizer;
    private final ObjectMapper objectMapper;

    public CompanyFinanceAnalysisService(JobPostingRepository postings, JdbcTemplate jdbc,
                                         CompanyNameNormalizer normalizer, ObjectMapper objectMapper) {
        this.postings = postings;
        this.jdbc = jdbc;
        this.normalizer = normalizer;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CompanyFinanceAnalysisResponse get(long jobPostingId) {
        JobPosting posting = postings.findById(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("채용공고를 찾을 수 없습니다."));
        String sourceCompanyId = posting.getSourceCompanyId() == null ? "" : posting.getSourceCompanyId();
        List<CompanyFinanceAnalysisResponse.MatchRow> matches = jdbc.query(MATCH_SQL,
                (rs, rowNum) -> new CompanyFinanceAnalysisResponse.MatchRow(rs.getString(1), rs.getString(2)),
                posting.getSourceProvider(), sourceCompanyId, normalizer.normalize(posting.getCompanyName()));
        if (matches.isEmpty() || !"CONFIRMED".equals(matches.getFirst().status())) {
            return noFinance("UNMATCHED", "DART 공시법인과 연결할 수 없어 재무정보를 제공하지 않습니다.", null);
        }

        String corpCode = matches.getFirst().corpCode();
        List<CompanyFinanceAnalysisResponse.FinancialYear> financials = jdbc.query(FINANCIALS_SQL,
                (rs, rowNum) -> new CompanyFinanceAnalysisResponse.FinancialYear(
                        rs.getInt("business_year"), nullableLong(rs.getObject("revenue")),
                        nullableLong(rs.getObject("operating_income")), nullableLong(rs.getObject("net_income")),
                        nullableLong(rs.getObject("total_assets")), nullableLong(rs.getObject("total_liabilities")),
                        nullableLong(rs.getObject("total_equity")), nullableLong(rs.getObject("operating_cash_flow")),
                        rs.getString("fs_div"), rs.getString("rcept_no")), corpCode);
        if (financials.isEmpty()) {
            return noFinance("FINANCIALS_NOT_FOUND", "이 기업은 DART에서 조회 가능한 재무제표를 찾지 못했습니다.", corpCode);
        }
        if (financials.size() < 3) {
            return new CompanyFinanceAnalysisResponse("DATA_INSUFFICIENT",
                    "성장 전망을 계산하기 위한 최근 3개년 재무 데이터가 부족합니다.", corpCode, financials, null);
        }
        List<CompanyFinanceAnalysisResponse.Forecast> forecasts = jdbc.query(FORECAST_SQL,
                (rs, rowNum) -> new CompanyFinanceAnalysisResponse.Forecast(
                        rs.getInt("base_year"), rs.getString("outlook"), rs.getString("confidence"),
                        rs.getDouble("growth_probability"),
                        rs.getDouble("profitability_improvement_probability"),
                        rs.getDouble("stability_risk_probability"), rs.getString("model_version"),
                        parseEvidence(rs.getString("evidence")), timestampText(rs.getTimestamp("generated_at"))),
                corpCode);
        if (!forecasts.isEmpty()) {
            return new CompanyFinanceAnalysisResponse("READY",
                    "검증된 모델의 저장된 전망과 DART 재무 추이를 제공합니다.",
                    corpCode, financials, forecasts.getFirst());
        }
        return new CompanyFinanceAnalysisResponse("FINANCIALS_ONLY",
                "재무제표 분석은 준비되었습니다. 검증된 예측 모델 결과가 등록되면 전망을 함께 표시합니다.",
                corpCode, financials, null);
    }

    private List<String> parseEvidence(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String timestampText(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private CompanyFinanceAnalysisResponse noFinance(String status, String message, String corpCode) {
        return new CompanyFinanceAnalysisResponse(status, message, corpCode, List.of(), null);
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}

package com.jobpilot.api.domain.companyfinance.service;

import com.jobpilot.api.domain.companyfinance.dto.CompanyFinanceAnalysisResponse;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
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

    private final JobPostingRepository postings;
    private final JdbcTemplate jdbc;
    private final CompanyNameNormalizer normalizer;

    public CompanyFinanceAnalysisService(JobPostingRepository postings, JdbcTemplate jdbc,
                                         CompanyNameNormalizer normalizer) {
        this.postings = postings;
        this.jdbc = jdbc;
        this.normalizer = normalizer;
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
        return new CompanyFinanceAnalysisResponse("FINANCIALS_ONLY",
                "재무제표 분석은 준비되었습니다. 검증된 예측 모델 결과가 등록되면 전망을 함께 표시합니다.",
                corpCode, financials, null);
    }

    private CompanyFinanceAnalysisResponse noFinance(String status, String message, String corpCode) {
        return new CompanyFinanceAnalysisResponse(status, message, corpCode, List.of(), null);
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}

package com.jobpilot.api.domain.companyfinance.service;

import com.jobpilot.api.domain.companyfinance.client.OpenDartClient;
import com.jobpilot.api.domain.companyfinance.client.OpenDartFinancialSnapshot;
import com.jobpilot.api.domain.companyfinance.client.OpenDartNoDataException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;

@Service
public class CompanyFinancialSyncService {
    private static final Logger log = LoggerFactory.getLogger(CompanyFinancialSyncService.class);
    private static final String ANNUAL_REPORT_CODE = "11011";
    private static final String CFS = "CFS";
    private static final String CONFIRMED_CORPORATIONS = """
            SELECT DISTINCT corp_code
            FROM company_dart_matches
            WHERE match_status = 'CONFIRMED' AND corp_code IS NOT NULL
            """;
    private static final String UPSERT_FINANCIAL_YEAR = """
            INSERT INTO company_financial_years (
                corp_code, business_year, report_code, fs_div, revenue, operating_income, net_income,
                total_assets, total_liabilities, total_equity, operating_cash_flow)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE revenue = VALUES(revenue), operating_income = VALUES(operating_income),
                net_income = VALUES(net_income), total_assets = VALUES(total_assets),
                total_liabilities = VALUES(total_liabilities), total_equity = VALUES(total_equity),
                operating_cash_flow = VALUES(operating_cash_flow), fetched_at = NOW()
            """;

    private final OpenDartClient client;
    private final JdbcTemplate jdbc;

    public CompanyFinancialSyncService(OpenDartClient client, JdbcTemplate jdbc) {
        this.client = client;
        this.jdbc = jdbc;
    }

    public int syncConfirmedCompanies(int firstYear, int lastYear) {
        List<String> corpCodes = jdbc.query(CONFIRMED_CORPORATIONS, (rs, rowNum) -> rs.getString(1));
        int stored = 0;
        for (String corpCode : corpCodes) {
            for (int year = firstYear; year <= lastYear; year++) {
                stored += syncAnnualStatement(corpCode, year);
            }
        }
        return stored;
    }

    private int syncAnnualStatement(String corpCode, int businessYear) {
        OpenDartFinancialSnapshot snapshot;
        try {
            snapshot = fetchWithRetry(corpCode, businessYear);
        } catch (OpenDartNoDataException noStatementAvailable) {
            return 0;
        } catch (ResourceAccessException transientNetworkFailure) {
            // Preserve other companies' committed rows; a later idempotent run fills this gap.
            log.warn("OpenDART annual statement network failure after retries: corpCode={}, year={}",
                    corpCode, businessYear);
            return 0;
        }
        jdbc.update(UPSERT_FINANCIAL_YEAR,
                corpCode, businessYear, ANNUAL_REPORT_CODE, CFS,
                snapshot.revenue(), snapshot.operatingIncome(), snapshot.netIncome(), snapshot.totalAssets(),
                snapshot.totalLiabilities(), snapshot.totalEquity(), snapshot.operatingCashFlow());
        return 1;
    }

    private OpenDartFinancialSnapshot fetchWithRetry(String corpCode, int businessYear) {
        ResourceAccessException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return client.fetchAnnualConsolidatedStatement(corpCode, businessYear);
            } catch (ResourceAccessException networkFailure) {
                lastFailure = networkFailure;
                if (attempt < 3) {
                    try {
                        Thread.sleep(attempt * 1000L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw networkFailure;
                    }
                }
            }
        }
        throw lastFailure;
    }
}

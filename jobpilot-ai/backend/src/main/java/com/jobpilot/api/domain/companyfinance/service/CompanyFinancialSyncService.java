package com.jobpilot.api.domain.companyfinance.service;

import com.jobpilot.api.domain.companyfinance.client.OpenDartClient;
import com.jobpilot.api.domain.companyfinance.client.OpenDartFinancialSnapshot;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyFinancialSyncService {
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

    @Transactional
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
            snapshot = client.fetchAnnualConsolidatedStatement(corpCode, businessYear);
        } catch (RuntimeException noStatementAvailable) {
            return 0;
        }
        jdbc.update(UPSERT_FINANCIAL_YEAR,
                corpCode, businessYear, ANNUAL_REPORT_CODE, CFS,
                snapshot.revenue(), snapshot.operatingIncome(), snapshot.netIncome(), snapshot.totalAssets(),
                snapshot.totalLiabilities(), snapshot.totalEquity(), snapshot.operatingCashFlow());
        return 1;
    }
}

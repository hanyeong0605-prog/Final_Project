package com.jobpilot.api.domain.companyfinance.service;

import com.jobpilot.api.domain.companyfinance.client.OpenDartClient;
import com.jobpilot.api.domain.companyfinance.client.OpenDartFinancialSnapshot;
import com.jobpilot.api.domain.companyfinance.client.OpenDartNoDataException;
import java.util.List;
import java.util.Map;
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
    private static final String CONFIRMED_LISTED_CORPORATIONS = """
            SELECT DISTINCT matches.corp_code, corporations.stock_code
            FROM company_dart_matches matches
            JOIN dart_corporations corporations ON corporations.corp_code = matches.corp_code
            WHERE matches.match_status = 'CONFIRMED'
              AND matches.corp_code IS NOT NULL
              AND corporations.stock_code IS NOT NULL
              AND corporations.stock_code <> ''
            """;
    /*
     * fnlttMultiAcnt returns a stock code with each row, so it is efficient for
     * listed corporations. It cannot identify unlisted corporations in its
     * response, however. Those confirmed DART matches must use the single
     * corporation endpoint instead of being silently excluded from the dataset.
     */
    private static final String CONFIRMED_UNLISTED_CORPORATIONS = """
            SELECT DISTINCT matches.corp_code
            FROM company_dart_matches matches
            JOIN dart_corporations corporations ON corporations.corp_code = matches.corp_code
            WHERE matches.match_status = 'CONFIRMED'
              AND matches.corp_code IS NOT NULL
              AND (corporations.stock_code IS NULL OR corporations.stock_code = '')
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
    private static final String DART_FINANCIAL_CORPORATIONS_FOR_YEAR = """
            SELECT DISTINCT corp_code FROM company_financial_years
            WHERE business_year=? AND report_code='11011' AND data_source='DART'
              AND net_income IS NOT NULL
            """;

    private final OpenDartClient client;
    private final JdbcTemplate jdbc;

    public CompanyFinancialSyncService(OpenDartClient client, JdbcTemplate jdbc) {
        this.client = client;
        this.jdbc = jdbc;
    }

    public int syncConfirmedCompanies(int firstYear, int lastYear) {
        List<CorporationReference> corporations = jdbc.query(CONFIRMED_LISTED_CORPORATIONS,
                (rs, rowNum) -> new CorporationReference(rs.getString(1), rs.getString(2)));
        List<String> unlistedCorporations = jdbc.queryForList(CONFIRMED_UNLISTED_CORPORATIONS, String.class);
        int stored = 0;
        for (int year = firstYear; year <= lastYear; year++) {
            List<String> existingCorpCodes = jdbc.queryForList(DART_FINANCIAL_CORPORATIONS_FOR_YEAR, String.class, year);
            java.util.Set<String> existing = java.util.Set.copyOf(existingCorpCodes);
            List<CorporationReference> missingListed = corporations.stream()
                    .filter(corporation -> !existing.contains(corporation.corpCode()))
                    .toList();
            for (int start = 0; start < missingListed.size(); start += 100) {
                List<CorporationReference> batch = missingListed.subList(
                        start, Math.min(start + 100, missingListed.size()));
                List<String> corpCodes = batch.stream().map(CorporationReference::corpCode).toList();
                Map<String, OpenDartFinancialSnapshot> byStockCode = fetchBatchWithRetry(corpCodes, year);
                for (CorporationReference corporation : batch) {
                    OpenDartFinancialSnapshot snapshot = byStockCode.get(corporation.stockCode());
                    if (snapshot != null) {
                        storeAnnualStatement(corporation.corpCode(), year, snapshot);
                        stored++;
                    }
                }
            }
        }
        for (String corpCode : unlistedCorporations) {
            for (int year = firstYear; year <= lastYear; year++) {
                List<String> existingCorpCodes = jdbc.queryForList(DART_FINANCIAL_CORPORATIONS_FOR_YEAR, String.class, year);
                if (existingCorpCodes.contains(corpCode)) continue;
                OpenDartFinancialSnapshot snapshot = fetchSingleWithRetry(corpCode, year);
                if (snapshot != null) {
                    storeAnnualStatement(corpCode, year, snapshot);
                    stored++;
                }
            }
        }
        return stored;
    }

    private Map<String, OpenDartFinancialSnapshot> fetchBatchWithRetry(List<String> corpCodes, int businessYear) {
        try {
            return fetchMultipleWithRetry(corpCodes, businessYear);
        } catch (OpenDartNoDataException noStatementAvailable) {
            return Map.of();
        } catch (ResourceAccessException transientNetworkFailure) {
            log.warn("OpenDART multiple-company network failure after retries: batchSize={}, year={}",
                    corpCodes.size(), businessYear);
            return Map.of();
        }
    }

    private void storeAnnualStatement(String corpCode, int businessYear, OpenDartFinancialSnapshot snapshot) {
        jdbc.update(UPSERT_FINANCIAL_YEAR,
                corpCode, businessYear, ANNUAL_REPORT_CODE, CFS,
                snapshot.revenue(), snapshot.operatingIncome(), snapshot.netIncome(), snapshot.totalAssets(),
                snapshot.totalLiabilities(), snapshot.totalEquity(), snapshot.operatingCashFlow());
    }

    private Map<String, OpenDartFinancialSnapshot> fetchMultipleWithRetry(List<String> corpCodes, int businessYear) {
        ResourceAccessException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return client.fetchMultipleAnnualStatements(corpCodes, businessYear);
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

    private OpenDartFinancialSnapshot fetchSingleWithRetry(String corpCode, int businessYear) {
        ResourceAccessException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                // Spread the one-corporation fallback requests across the DART
                // quota while still keeping the explicit one-shot backfill bounded.
                Thread.sleep(120L);
                return client.fetchAnnualConsolidatedStatement(corpCode, businessYear);
            } catch (OpenDartNoDataException noStatementAvailable) {
                return null;
            } catch (ResourceAccessException networkFailure) {
                lastFailure = networkFailure;
                if (attempt < 3) continue;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("DART unlisted-company sync was interrupted", interrupted);
            }
        }
        throw lastFailure;
    }

    private record CorporationReference(String corpCode, String stockCode) {}
}

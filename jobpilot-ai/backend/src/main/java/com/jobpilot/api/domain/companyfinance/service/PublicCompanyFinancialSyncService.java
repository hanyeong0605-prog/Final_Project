package com.jobpilot.api.domain.companyfinance.service;

import com.jobpilot.api.domain.companyfinance.client.OpenDartClient;
import com.jobpilot.api.domain.companyfinance.client.PublicCompanyFinancialClient;
import com.jobpilot.api.domain.companyfinance.client.PublicCompanyFinancialSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Adds only missing DART annual years; it never overwrites a DART statement. */
@Service
public class PublicCompanyFinancialSyncService {
    private final JdbcTemplate jdbc;
    private final OpenDartClient dart;
    private final PublicCompanyFinancialClient publicFinance;

    public PublicCompanyFinancialSyncService(JdbcTemplate jdbc, OpenDartClient dart, PublicCompanyFinancialClient publicFinance) {
        this.jdbc = jdbc; this.dart = dart; this.publicFinance = publicFinance;
    }

    public int syncMissingAnnualYears(int fromYear, int toYear) {
        List<String> corpCodes = jdbc.queryForList("""
                SELECT DISTINCT m.corp_code FROM company_dart_matches m
                WHERE m.match_status='CONFIRMED' AND m.corp_code IS NOT NULL
                """, String.class);
        int stored = 0;
        for (String corpCode : corpCodes) {
            Optional<String> registration = registrationNumber(corpCode);
            if (registration.isEmpty()) continue;
            for (int year = fromYear; year <= toYear; year++) {
                Integer dartRows = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM company_financial_years
                        WHERE corp_code=? AND business_year=? AND report_code='11011' AND data_source='DART'
                        """, Integer.class, corpCode, year);
                if (dartRows != null && dartRows > 0) continue;
                Optional<PublicCompanyFinancialSnapshot> snapshot = publicFinance.fetchSummary(registration.get(), year);
                if (snapshot.isPresent()) { store(corpCode, year, snapshot.get()); stored++; }
            }
        }
        return stored;
    }

    private Optional<String> registrationNumber(String corpCode) {
        String stored = jdbc.query("SELECT jurir_no FROM dart_corporations WHERE corp_code=?", rs -> rs.next() ? rs.getString(1) : null, corpCode);
        if (stored != null && !stored.isBlank()) return Optional.of(stored);
        Optional<String> fetched = dart.fetchCorporateRegistrationNumber(corpCode);
        fetched.ifPresent(value -> jdbc.update("UPDATE dart_corporations SET jurir_no=? WHERE corp_code=?", value, corpCode));
        return fetched;
    }

    private void store(String corpCode, int year, PublicCompanyFinancialSnapshot value) {
        jdbc.update("""
                INSERT INTO company_financial_years(corp_code,business_year,report_code,fs_div,currency,data_source,revenue,operating_income,net_income,total_assets,total_liabilities,total_equity)
                VALUES (?,?,'11011','PUBLIC_SUMMARY','KRW','FINANCIAL_COMMISSION',?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE revenue=VALUES(revenue),operating_income=VALUES(operating_income),net_income=VALUES(net_income),total_assets=VALUES(total_assets),total_liabilities=VALUES(total_liabilities),total_equity=VALUES(total_equity),fetched_at=CURRENT_TIMESTAMP
                """, corpCode, year, value.revenue(), value.operatingIncome(), value.netIncome(), value.totalAssets(), value.totalLiabilities(), value.totalEquity());
    }
}

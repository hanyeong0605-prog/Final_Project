package com.jobpilot.api.domain.companyfinance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DartCompanyFinanceMigrationTest {

    @Test
    void financeMigrationDefinesTraceableCompanyFinanceTables() throws IOException {
        var stream = getClass().getResourceAsStream("/db/migration/V41__dart_company_finance.sql");
        assertNotNull(stream, "DART finance Flyway migration must exist");
        String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

        assertTrue(sql.contains("create table dart_corporations"));
        assertTrue(sql.contains("create table company_dart_matches"));
        assertTrue(sql.contains("create table company_financial_years"));
        assertTrue(sql.contains("create table company_financial_metrics"));
        assertTrue(sql.contains("create table company_growth_predictions"));
        assertTrue(sql.contains("rcept_no"));
        assertTrue(sql.contains("fs_div"));
    }
}

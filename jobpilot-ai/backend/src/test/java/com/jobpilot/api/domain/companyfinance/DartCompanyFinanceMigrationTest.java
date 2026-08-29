package com.jobpilot.api.domain.companyfinance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DartCompanyFinanceMigrationTest {

    @Test
    void financeMigrationDefinesTraceableCompanyFinanceTables() throws IOException {
        var stream = getClass().getResourceAsStream("/db/migration/V47__dart_company_finance.sql");
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

    @Test
    void flywayMigrationVersionsAreUnique() throws IOException {
        Pattern version = Pattern.compile("^(V\\d+)__.+\\.sql$");
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            long migrationCount = files
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> version.matcher(name).matches())
                    .count();
            try (var versions = Files.list(Path.of("src/main/resources/db/migration"))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(version::matcher)
                    .filter(java.util.regex.Matcher::matches)
                    .map(matcher -> matcher.group(1))
                    .distinct()) {
                assertTrue(migrationCount == versions.count(), "Flyway migration versions must be unique");
            }
        }
    }
}

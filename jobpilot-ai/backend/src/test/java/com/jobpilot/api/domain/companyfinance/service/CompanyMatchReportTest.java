package com.jobpilot.api.domain.companyfinance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CompanyMatchReportTest {
    @Test
    void countsEachMatchStatusForInternalCoverageReport() {
        var report = CompanyMatchReport.from(List.of(
                CompanyMatchStatus.CONFIRMED, CompanyMatchStatus.CONFIRMED,
                CompanyMatchStatus.CANDIDATE, CompanyMatchStatus.UNMATCHED));

        assertEquals(4, report.distinctCompanies());
        assertEquals(2, report.confirmed());
        assertEquals(1, report.candidates());
        assertEquals(1, report.unmatched());
    }
}

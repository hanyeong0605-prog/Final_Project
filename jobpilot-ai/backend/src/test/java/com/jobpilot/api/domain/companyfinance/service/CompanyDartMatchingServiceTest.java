package com.jobpilot.api.domain.companyfinance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CompanyDartMatchingServiceTest {
    private final CompanyDartMatchingService service = new CompanyDartMatchingService(new CompanyNameNormalizer());

    @Test
    void confirmsOnlyExactNormalizedCorporationName() {
        var result = service.match("(주) 플리토", List.of(
                new DartCorporationCandidate("01399999", "주식회사 플리토"),
                new DartCorporationCandidate("01400000", "플리토테크")));

        assertEquals(CompanyMatchStatus.CONFIRMED, result.status());
        assertEquals("01399999", result.corpCode());
    }

    @Test
    void leavesSimilarNameAsCandidateRatherThanConfirmingIt() {
        var result = service.match("플리토", List.of(new DartCorporationCandidate("01400000", "플리토테크")));

        assertEquals(CompanyMatchStatus.CANDIDATE, result.status());
        assertEquals(null, result.corpCode());
    }
}

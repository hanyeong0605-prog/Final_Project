package com.jobpilot.api.domain.companyfinance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CompanyNameNormalizerTest {
    private final CompanyNameNormalizer normalizer = new CompanyNameNormalizer();

    @Test
    void removesKoreanCorporateMarkersWhitespaceAndPunctuation() {
        assertEquals("플리토", normalizer.normalize(" 주식회사 (주) 플리토 "));
    }

    @Test
    void returnsEmptyTextForNullOrMarkerOnlyName() {
        assertEquals("", normalizer.normalize(null));
        assertEquals("", normalizer.normalize("(주) 주식회사"));
    }
}

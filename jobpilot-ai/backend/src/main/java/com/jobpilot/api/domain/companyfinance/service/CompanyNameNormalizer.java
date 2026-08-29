package com.jobpilot.api.domain.companyfinance.service;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CompanyNameNormalizer {
    public String normalize(String rawName) {
        if (rawName == null || rawName.isBlank()) return "";
        return rawName
                .toLowerCase(Locale.ROOT)
                .replaceAll("주식회사|\\(주\\)|㈜", "")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHangul}]", "")
                .trim();
    }
}

package com.jobpilot.api.domain.companyfinance.service;

import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
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

    /** Keeps both the legal name and parenthesised crawler aliases, for example EA Korea. */
    public Set<String> aliases(String rawName) {
        Set<String> aliases = new LinkedHashSet<>();
        String primary = normalize(rawName);
        if (!primary.isEmpty()) aliases.add(primary);
        if (rawName != null) {
            var matcher = java.util.regex.Pattern.compile("\\(([^)]+)\\)").matcher(rawName);
            while (matcher.find()) {
                String alias = normalize(matcher.group(1));
                if (!alias.isEmpty()) aliases.add(alias);
            }
        }
        return aliases;
    }
}

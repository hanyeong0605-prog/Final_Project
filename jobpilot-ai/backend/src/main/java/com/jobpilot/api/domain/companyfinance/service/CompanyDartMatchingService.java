package com.jobpilot.api.domain.companyfinance.service;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CompanyDartMatchingService {
    private final CompanyNameNormalizer normalizer;

    public CompanyDartMatchingService(CompanyNameNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public CompanyMatchDecision match(String companyName, List<DartCorporationCandidate> corporations) {
        var companyAliases = normalizer.aliases(companyName);
        if (companyAliases.isEmpty()) return new CompanyMatchDecision(CompanyMatchStatus.UNMATCHED, null);

        for (DartCorporationCandidate corporation : corporations) {
            var corporationAliases = normalizer.aliases(corporation.corpName());
            corporationAliases.addAll(normalizer.aliases(corporation.corpEngName()));
            if (corporationAliases.stream().anyMatch(companyAliases::contains)) {
                return new CompanyMatchDecision(CompanyMatchStatus.CONFIRMED, corporation.corpCode());
            }
        }
        for (DartCorporationCandidate corporation : corporations) {
            var corporationAliases = normalizer.aliases(corporation.corpName());
            corporationAliases.addAll(normalizer.aliases(corporation.corpEngName()));
            if (corporationAliases.stream().anyMatch(candidate -> companyAliases.stream()
                    .anyMatch(company -> candidate.contains(company) || company.contains(candidate)))) {
                return new CompanyMatchDecision(CompanyMatchStatus.CANDIDATE, null);
            }
        }
        return new CompanyMatchDecision(CompanyMatchStatus.UNMATCHED, null);
    }
}

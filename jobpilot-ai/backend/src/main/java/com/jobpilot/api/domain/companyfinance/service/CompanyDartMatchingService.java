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
        String normalizedName = normalizer.normalize(companyName);
        if (normalizedName.isEmpty()) return new CompanyMatchDecision(CompanyMatchStatus.UNMATCHED, null);

        for (DartCorporationCandidate corporation : corporations) {
            if (normalizedName.equals(normalizer.normalize(corporation.corpName()))) {
                return new CompanyMatchDecision(CompanyMatchStatus.CONFIRMED, corporation.corpCode());
            }
        }
        for (DartCorporationCandidate corporation : corporations) {
            String candidateName = normalizer.normalize(corporation.corpName());
            if (!candidateName.isEmpty() && (candidateName.contains(normalizedName) || normalizedName.contains(candidateName))) {
                return new CompanyMatchDecision(CompanyMatchStatus.CANDIDATE, null);
            }
        }
        return new CompanyMatchDecision(CompanyMatchStatus.UNMATCHED, null);
    }
}

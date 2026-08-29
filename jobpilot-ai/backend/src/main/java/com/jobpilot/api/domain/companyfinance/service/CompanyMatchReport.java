package com.jobpilot.api.domain.companyfinance.service;

import java.util.Collection;

public record CompanyMatchReport(int distinctCompanies, int confirmed, int candidates, int unmatched) {
    public static CompanyMatchReport from(Collection<CompanyMatchStatus> statuses) {
        int confirmed = 0, candidates = 0, unmatched = 0;
        for (CompanyMatchStatus status : statuses) {
            switch (status) {
                case CONFIRMED -> confirmed++;
                case CANDIDATE -> candidates++;
                case UNMATCHED -> unmatched++;
            }
        }
        return new CompanyMatchReport(statuses.size(), confirmed, candidates, unmatched);
    }
}

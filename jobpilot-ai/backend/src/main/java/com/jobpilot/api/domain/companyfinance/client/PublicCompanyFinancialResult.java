package com.jobpilot.api.domain.companyfinance.client;

import java.util.Optional;

/**
 * Keeps a public-finance API error distinct from a successful request that simply has no row
 * for a corporation/year.  The fallback batch must expose this distinction in its diagnostics.
 */
public record PublicCompanyFinancialResult(Optional<PublicCompanyFinancialSnapshot> snapshot,
                                           String resultCode, String resultMessage) {
    public static PublicCompanyFinancialResult success(PublicCompanyFinancialSnapshot snapshot) {
        return new PublicCompanyFinancialResult(Optional.of(snapshot), "00", "NORMAL SERVICE.");
    }

    public static PublicCompanyFinancialResult empty() {
        return new PublicCompanyFinancialResult(Optional.empty(), "00", "NO_DATA");
    }

    public static PublicCompanyFinancialResult failure(String code, String message) {
        return new PublicCompanyFinancialResult(Optional.empty(), code, message);
    }

    public boolean successfulRequest() {
        return "00".equals(resultCode);
    }
}

package com.jobpilot.api.domain.companyfinance.dto;

import java.util.List;

public record CompanyFinanceAnalysisResponse(
        String status,
        String message,
        String corpCode,
        List<FinancialYear> financials,
        Forecast forecast
) {
    public record MatchRow(String corpCode, String status) {}

    public record FinancialYear(
            int businessYear,
            Long revenue,
            Long operatingIncome,
            Long netIncome,
            Long totalAssets,
            Long totalLiabilities,
            Long totalEquity,
            Long operatingCashFlow,
            String fsDiv,
            String receiptNumber
    ) {}

    public record Forecast(
            String outlook,
            String confidence,
            Double growthProbability,
            Double profitabilityImprovementProbability,
            Double stabilityRiskProbability,
            String modelVersion
    ) {}
}

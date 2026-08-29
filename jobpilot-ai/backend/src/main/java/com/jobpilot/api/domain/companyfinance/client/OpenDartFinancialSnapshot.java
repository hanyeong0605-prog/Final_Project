package com.jobpilot.api.domain.companyfinance.client;

public record OpenDartFinancialSnapshot(
        Long revenue,
        Long operatingIncome,
        Long netIncome,
        Long totalAssets,
        Long totalLiabilities,
        Long totalEquity,
        Long operatingCashFlow
) {}

package com.jobpilot.api.domain.companyfinance.client;

/** Finance Commission summary values are an explicit fallback when DART has no annual statement. */
public record PublicCompanyFinancialSnapshot(Long revenue, Long operatingIncome, Long netIncome,
                                             Long totalAssets, Long totalLiabilities, Long totalEquity) {}

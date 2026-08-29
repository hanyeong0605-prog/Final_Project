package com.jobpilot.api.domain.companyfinance.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class OpenDartFinancialStatementParser {
    private final ObjectMapper objectMapper;

    public OpenDartFinancialStatementParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OpenDartFinancialSnapshot parse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String status = root.path("status").asText();
        if ("013".equals(status)) {
            throw new OpenDartNoDataException();
        }
        if (!"000".equals(status)) {
            // Do not silently turn invalid keys, rate limits, or upstream failures into missing data.
            throw new IOException("OpenDART financial statement request failed with status=" + status);
        }
        Long revenue = null, operatingIncome = null, netIncome = null, totalAssets = null,
                totalLiabilities = null, totalEquity = null, operatingCashFlow = null;
        for (JsonNode account : root.path("list")) {
            String name = account.path("account_nm").asText();
            Long amount = parseAmount(account.path("thstrm_amount").asText());
            if (amount == null) continue;
            switch (name) {
                case "매출액", "수익(매출액)" -> revenue = amount;
                case "영업이익" -> operatingIncome = amount;
                case "당기순이익" -> netIncome = amount;
                case "자산총계" -> totalAssets = amount;
                case "부채총계" -> totalLiabilities = amount;
                case "자본총계" -> totalEquity = amount;
                default -> {
                    if (name.contains("영업활동") && name.contains("현금흐름")) operatingCashFlow = amount;
                }
            }
        }
        return new OpenDartFinancialSnapshot(revenue, operatingIncome, netIncome, totalAssets,
                totalLiabilities, totalEquity, operatingCashFlow);
    }

    private Long parseAmount(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank() || "-".equals(rawAmount.trim())) return null;
        try {
            return Long.parseLong(rawAmount.replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

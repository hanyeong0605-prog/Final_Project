package com.jobpilot.api.domain.companyfinance.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenDartFinancialStatementParser {
    private final ObjectMapper objectMapper;

    public OpenDartFinancialStatementParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OpenDartFinancialSnapshot parse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        validateStatus(root);
        Accounts accounts = new Accounts();
        for (JsonNode account : root.path("list")) accounts.accept(account);
        return accounts.snapshot();
    }

    public Map<String, OpenDartFinancialSnapshot> parseMultiple(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        validateStatus(root);
        Map<String, Accounts> grouped = new LinkedHashMap<>();
        for (JsonNode account : root.path("list")) {
            if (!"CFS".equals(account.path("fs_div").asText())) continue;
            String corpCode = account.path("corp_code").asText();
            if (!corpCode.isBlank()) grouped.computeIfAbsent(corpCode, ignored -> new Accounts()).accept(account);
        }
        Map<String, OpenDartFinancialSnapshot> result = new LinkedHashMap<>();
        grouped.forEach((corpCode, accounts) -> result.put(corpCode, accounts.snapshot()));
        return result;
    }

    private void validateStatus(JsonNode root) throws IOException {
        String status = root.path("status").asText();
        if ("013".equals(status)) {
            throw new OpenDartNoDataException();
        }
        if (!"000".equals(status)) {
            // Do not silently turn invalid keys, rate limits, or upstream failures into missing data.
            throw new IOException("OpenDART financial statement request failed with status=" + status);
        }
    }

    private final class Accounts {
        private Long revenue, operatingIncome, netIncome, totalAssets, totalLiabilities, totalEquity,
                operatingCashFlow;

        void accept(JsonNode account) {
            String name = account.path("account_nm").asText();
            Long amount = parseAmount(account.path("thstrm_amount").asText());
            if (amount == null) return;
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

        OpenDartFinancialSnapshot snapshot() {
            return new OpenDartFinancialSnapshot(revenue, operatingIncome, netIncome, totalAssets,
                    totalLiabilities, totalEquity, operatingCashFlow);
        }
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

package com.jobpilot.api.domain.companyfinance.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PublicCompanyFinancialParser {
    private final ObjectMapper json;

    public PublicCompanyFinancialParser(ObjectMapper json) { this.json = json; }

    public Optional<PublicCompanyFinancialSnapshot> parse(String body) {
        try {
            JsonNode items = json.readTree(body).path("response").path("body").path("items").path("item");
            JsonNode item = items.isArray() ? items.path(0) : items;
            if (item.isMissingNode() || item.isNull()) return Optional.empty();
            return Optional.of(new PublicCompanyFinancialSnapshot(
                    amount(item, "enpSaleAmt"), amount(item, "enpBzopPft"), amount(item, "enpCrtmNpf"),
                    amount(item, "enpTastAmt"), amount(item, "enpTdbtAmt"), amount(item, "enpTcptAmt")));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Long amount(JsonNode item, String field) {
        String value = item.path(field).asText("").replace(",", "").trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) return null;
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return null; }
    }
}

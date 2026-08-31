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
        return parseResult(body).snapshot();
    }

    public PublicCompanyFinancialResult parseResult(String body) {
        try {
            JsonNode response = json.readTree(body).path("response");
            String resultCode = response.path("header").path("resultCode").asText("").trim();
            String resultMessage = response.path("header").path("resultMsg").asText("").trim();
            if (!resultCode.isBlank() && !"00".equals(resultCode)) {
                return PublicCompanyFinancialResult.failure(
                        resultCode, resultMessage.isBlank() ? "Public finance API returned an error." : resultMessage);
            }
            JsonNode items = response.path("body").path("items").path("item");
            JsonNode item = items.isArray() ? items.path(0) : items;
            if (item.isMissingNode() || item.isNull()) {
                return resultCode.isBlank()
                        ? PublicCompanyFinancialResult.failure("MALFORMED_RESPONSE", "Public finance response has no header or item.")
                        : PublicCompanyFinancialResult.empty();
            }
            return PublicCompanyFinancialResult.success(new PublicCompanyFinancialSnapshot(
                    amount(item, "enpSaleAmt"), amount(item, "enpBzopPft"), amount(item, "enpCrtmNpf"),
                    amount(item, "enpTastAmt"), amount(item, "enpTdbtAmt"), amount(item, "enpTcptAmt")));
        } catch (Exception error) {
            return PublicCompanyFinancialResult.failure("MALFORMED_RESPONSE", "Public finance response could not be parsed.");
        }
    }

    private Long amount(JsonNode item, String field) {
        String value = item.path(field).asText("").replace(",", "").trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) return null;
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return null; }
    }
}

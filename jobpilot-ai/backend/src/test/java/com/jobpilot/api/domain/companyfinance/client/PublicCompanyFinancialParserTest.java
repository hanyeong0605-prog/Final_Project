package com.jobpilot.api.domain.companyfinance.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PublicCompanyFinancialParserTest {
    private final PublicCompanyFinancialParser parser = new PublicCompanyFinancialParser(new ObjectMapper());

    @Test
    void parsesTheFinancialCommissionSummaryResponse() {
        String body = """
                {"response":{"body":{"items":{"item":[{"enpSaleAmt":"1200","enpBzopPft":"100","enpCrtmNpf":"70","enpTastAmt":"2000","enpTdbtAmt":"700","enpTcptAmt":"1300"}]}}}}
                """;

        var result = parser.parse(body);

        assertTrue(result.isPresent());
        assertEquals(new PublicCompanyFinancialSnapshot(1200L, 100L, 70L, 2000L, 700L, 1300L), result.get());
    }
}

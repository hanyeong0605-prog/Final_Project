package com.jobpilot.api.domain.companyfinance.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenDartFinancialStatementParserTest {
    @Test
    void extractsCoreAccountsFromAnnualDartResponse() throws Exception {
        String response = """
                {"status":"000","list":[
                  {"account_nm":"매출액","thstrm_amount":"1,200"},
                  {"account_nm":"영업이익","thstrm_amount":"-50"},
                  {"account_nm":"당기순이익","thstrm_amount":"30"},
                  {"account_nm":"자산총계","thstrm_amount":"2,000"},
                  {"account_nm":"부채총계","thstrm_amount":"700"},
                  {"account_nm":"자본총계","thstrm_amount":"1,300"},
                  {"account_nm":"영업활동으로 인한 현금흐름","thstrm_amount":"90"}
                ]}
                """;

        var result = new OpenDartFinancialStatementParser(new ObjectMapper()).parse(response);

        assertEquals(1200L, result.revenue());
        assertEquals(-50L, result.operatingIncome());
        assertEquals(30L, result.netIncome());
        assertEquals(2000L, result.totalAssets());
        assertEquals(700L, result.totalLiabilities());
        assertEquals(1300L, result.totalEquity());
        assertEquals(90L, result.operatingCashFlow());
    }

    @Test
    void distinguishesExpectedNoDataFromAuthenticationOrRateLimitFailures() {
        var parser = new OpenDartFinancialStatementParser(new ObjectMapper());

        assertThrows(OpenDartNoDataException.class,
                () -> parser.parse("{\"status\":\"013\",\"message\":\"no data\"}"));
        var failure = assertThrows(java.io.IOException.class,
                () -> parser.parse("{\"status\":\"020\",\"message\":\"request limit\"}"));
        org.assertj.core.api.Assertions.assertThat(failure.getMessage()).contains("status=020");
    }

    @Test
    void groupsMultipleCorporationAccountsAndUsesConsolidatedStatementsOnly() throws Exception {
        String response = """
                {"status":"000","list":[
                  {"stock_code":"005930","fs_div":"CFS","account_nm":"매출액","thstrm_amount":"1,200"},
                  {"stock_code":"005930","fs_div":"OFS","account_nm":"매출액","thstrm_amount":"999"},
                  {"stock_code":"005930","fs_div":"CFS","account_nm":"영업이익","thstrm_amount":"100"},
                  {"stock_code":"000660","fs_div":"CFS","account_nm":"수익(매출액)","thstrm_amount":"2,500"},
                  {"stock_code":"000660","fs_div":"CFS","account_nm":"부채총계","thstrm_amount":"800"}
                ]}
                """;

        var result = new OpenDartFinancialStatementParser(new ObjectMapper()).parseMultiple(response);

        assertEquals(2, result.size());
        assertEquals(1200L, result.get("005930").revenue());
        assertEquals(100L, result.get("005930").operatingIncome());
        assertEquals(2500L, result.get("000660").revenue());
        assertEquals(800L, result.get("000660").totalLiabilities());
    }
}

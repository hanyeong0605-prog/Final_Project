package com.jobpilot.api.domain.companyfinance.client;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CompanyGrowthModelClientTest {
    private MockRestServiceServer server;
    private CompanyGrowthModelClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("http://ai.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CompanyGrowthModelClient(builder.build(), "internal-test-key");
    }

    private String response(boolean validated, double growthProbability) {
        return """
                {"modelVersion":"company-growth-rf-v1","validated":%s,
                 "growthProbability":%s,"profitabilityImprovementProbability":0.61,
                 "stabilityRiskProbability":0.18,"expectedRevenueGrowth":0.12,
                 "outlook":"POSITIVE","confidence":"HIGH"}
                """.formatted(validated, growthProbability);
    }

    @Test
    void acceptsOnlyAuthenticatedValidatedPrediction() {
        server.expect(requestTo("http://ai.test/company-finance/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "internal-test-key"))
                .andRespond(withSuccess(response(true, 0.72), MediaType.APPLICATION_JSON));

        var result = client.predict(Map.of("revenueGrowth1Y", 0.1)).orElseThrow();
        assertThat(result.modelVersion()).isEqualTo("company-growth-rf-v1");
        server.verify();
    }

    @Test
    void rejectsUnvalidatedOrOutOfRangePrediction() {
        server.expect(requestTo("http://ai.test/company-finance/predict"))
                .andRespond(withSuccess(response(false, 0.72), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ai.test/company-finance/predict"))
                .andRespond(withSuccess(response(true, 1.1), MediaType.APPLICATION_JSON));

        assertThat(client.predict(Map.of())).isEmpty();
        assertThat(client.predict(Map.of())).isEmpty();
        server.verify();
    }

    @Test
    void treatsUnavailableModelAsNoPrediction() {
        server.expect(requestTo("http://ai.test/company-finance/predict"))
                .andRespond(withServiceUnavailable());
        assertThat(client.predict(Map.of())).isEmpty();
        server.verify();
    }

    @Test
    void missingInternalKeyDoesNotCallAi() {
        var noKey = new CompanyGrowthModelClient(RestClient.create("http://unused.invalid"), "");
        assertThat(noKey.predict(Map.of())).isEmpty();
    }
}

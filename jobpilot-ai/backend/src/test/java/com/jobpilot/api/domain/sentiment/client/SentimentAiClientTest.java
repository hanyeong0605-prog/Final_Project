package com.jobpilot.api.domain.sentiment.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SentimentAiClientTest {
    private MockRestServiceServer server;
    private SentimentAiClient client;

    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://ai.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SentimentAiClient(builder.build(), "test-only-key");
    }

    private String response(String hash, String score) {
        return """
                {"modelVersion":"kote-test","policyVersion":"v1","contentHash":"%s",
                 "emotions":[{"label":"기쁨","score":0.7}],
                 "polarity":{"label":"MIXED","positive":%s,"neutral":0.2,"negative":0.6}}
                """.formatted(hash, score);
    }

    @Test void sendsAuthenticatedRequestAndKeepsIndependentScores() {
        server.expect(requestTo("http://ai.test/sentiment/analyze"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-Internal-Api-Key", "test-only-key"))
                .andExpect(content().json("{\"text\":\"좋아요\",\"topK\":5}"))
                .andRespond(withSuccess(response(SentimentAiClient.contentHash("좋아요"), "0.8"), MediaType.APPLICATION_JSON));
        var result = client.analyze("\u00a0좋아요\u0085").orElseThrow();
        assertThat(result.polarity().label()).isEqualTo("MIXED");
        assertThat(result.polarity().positive() + result.polarity().negative()).isGreaterThan(1);
        server.verify();
    }

    @Test void refusesResultForDifferentContent() {
        server.expect(requestTo("http://ai.test/sentiment/analyze"))
                .andRespond(withSuccess(response("wrong-hash", "0.8"), MediaType.APPLICATION_JSON));
        assertThat(client.analyze("좋아요")).isEmpty();
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"null", "1.1", "-0.1"})
    void rejectsInvalidScores(String score) {
        server.expect(requestTo("http://ai.test/sentiment/analyze"))
                .andRespond(withSuccess(response(SentimentAiClient.contentHash("좋아요"), score), MediaType.APPLICATION_JSON));
        assertThat(client.analyze("좋아요")).isEmpty();
        server.verify();
    }

    @Test void modelUnavailableIsNotNeutral() {
        server.expect(requestTo("http://ai.test/sentiment/analyze")).andRespond(withServiceUnavailable());
        assertThat(client.analyze("좋아요")).isEmpty();
        server.verify();
    }

    @Test void malformedResponseIsUnavailable() {
        server.expect(requestTo("http://ai.test/sentiment/analyze"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(client.analyze("좋아요")).isEmpty();
    }

    @Test void missingKeyDoesNotCallAi() {
        assertThat(new SentimentAiClient(RestClient.create("http://unused.invalid"), "").analyze("좋아요")).isEmpty();
    }

    @Test void invalidInputDoesNotCallAi() {
        assertThatIllegalArgumentException().isThrownBy(() -> client.analyze(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> client.analyze("가".repeat(5001)));
        assertThatIllegalArgumentException().isThrownBy(() -> client.analyze(null));
        server.verify();
    }
}

package com.jobpilot.api.domain.resume.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls the private AI service. Resume upload/save must still work if this service is unavailable. */
@Component
public class ResumeDocumentAiClient {
    private final RestClient restClient;
    private final String internalApiKey;

    public ResumeDocumentAiClient(
            @Value("${app.ai-server.base-url}") String aiServerBaseUrl,
            @Value("${app.internal-api-key:}") String internalApiKey
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(35));
        this.restClient = RestClient.builder().baseUrl(aiServerBaseUrl).requestFactory(requestFactory).build();
        this.internalApiKey = internalApiKey;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyze(String text) {
        return restClient.post().uri("/resume/document/analyze")
                .header("X-Internal-Api-Key", internalApiKey)
                .body(Map.of("text", empty(text))).retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generate(Map<String, Object> profile, List<String> answers, String templateKey, String templateHint) {
        return restClient.post().uri("/resume/document/generate")
                .header("X-Internal-Api-Key", internalApiKey)
                .body(Map.of("profile", profile, "answers", answers, "template_key", empty(templateKey), "template_hint", empty(templateHint)))
                .retrieve().body(Map.class);
    }

    private String empty(String value) { return value == null ? "" : value; }
}

package com.jobpilot.api.domain.assistant.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AssistantAiClient {
    private final RestClient client;
    private final String internalApiKey;

    public AssistantAiClient(@Value("${app.ai-server.base-url}") String baseUrl,
                             @Value("${app.internal-api-key:}") String internalApiKey) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.internalApiKey = internalApiKey;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(long memberId, String message, List<Map<String, String>> history) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new IllegalStateException("챗봇 내부 인증 키가 설정되지 않았습니다.");
        }
        try {
            return client.post().uri("/assistant/chat")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .body(Map.of("member_id", memberId, "message", message, "history", history))
                    .retrieve().body(Map.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("챗봇 서버에 연결하지 못했습니다.");
        }
    }
}

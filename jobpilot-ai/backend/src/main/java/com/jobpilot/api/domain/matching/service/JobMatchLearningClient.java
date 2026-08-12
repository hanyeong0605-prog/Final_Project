package com.jobpilot.api.domain.matching.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the local AI container only.  A failed or warming-up model never
 * prevents the rule/evidence based recommendation from being saved.
 */
@Component
public class JobMatchLearningClient {
    private final RestClient restClient;
    private final String internalApiKey;

    public JobMatchLearningClient(
            @Value("${app.ai-server.base-url}") String baseUrl,
            @Value("${app.internal-api-key:}") String internalApiKey
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.internalApiKey = internalApiKey;
    }

    @SuppressWarnings("unchecked")
    public LearningScores score(List<LearningCandidate> candidates) {
        if (candidates.isEmpty() || internalApiKey == null || internalApiKey.isBlank()) return LearningScores.warmingUp();
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/matching/score-batch")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .body(Map.of("candidates", candidates))
                    .retrieve()
                    .body(Map.class);
            if (response == null || !"ready".equals(response.get("state"))) return LearningScores.warmingUp();
            Object values = response.get("scores");
            if (!(values instanceof List<?> list) || list.size() != candidates.size()) return LearningScores.warmingUp();
            List<BigDecimal> scores = new ArrayList<>();
            for (Object value : list) scores.add(new BigDecimal(String.valueOf(value)));
            return new LearningScores(true, String.valueOf(response.getOrDefault("source", "RANDOM_FOREST_V1")), scores);
        } catch (Exception ignored) {
            return LearningScores.warmingUp();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> retrain() {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/matching/retrain")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve()
                    .body(Map.class);
            return response == null ? Map.of("state", "unavailable") : response;
        } catch (Exception ignored) {
            return Map.of("state", "unavailable");
        }
    }

    /** Blends readiness (65%) with learned interest likelihood (35%). */
    public BigDecimal blendedReadiness(BigDecimal ruleScore, BigDecimal interestProbability) {
        return ruleScore.multiply(BigDecimal.valueOf(0.65))
                .add(interestProbability.multiply(BigDecimal.valueOf(0.35)))
                .setScale(2, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100));
    }

    public record LearningCandidate(
            double skillCoverage,
            double certificateCoverage,
            double experienceMatch,
            double educationMatch,
            double ruleReadiness,
            int missingRequiredCount,
            String targetText,
            String jobText
    ) {}

    public record LearningScores(boolean ready, String source, List<BigDecimal> scores) {
        static LearningScores warmingUp() { return new LearningScores(false, "RULE_BASED_WARMUP", List.of()); }
    }
}

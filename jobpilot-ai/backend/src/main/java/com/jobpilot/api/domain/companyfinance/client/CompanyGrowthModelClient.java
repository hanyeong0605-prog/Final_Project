package com.jobpilot.api.domain.companyfinance.client;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls only the locally mounted, held-out-validated growth model. */
@Component
public class CompanyGrowthModelClient {
    private final RestClient client;
    private final String key;

    @Autowired
    public CompanyGrowthModelClient(@Value("${app.ai-server.base-url}") String baseUrl,
                                    @Value("${app.internal-api-key:}") String key) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.key = key;
    }

    CompanyGrowthModelClient(RestClient client, String key) {
        this.client = client;
        this.key = key;
    }

    public record Prediction(String modelVersion, Boolean validated, Double growthProbability,
                             Double profitabilityImprovementProbability, Double stabilityRiskProbability,
                             Double expectedRevenueGrowth, String outlook, String confidence) {}

    public Optional<Prediction> predict(Map<String, Object> features) {
        if (key == null || key.isBlank()) return Optional.empty();
        try {
            Prediction result = client.post().uri("/company-finance/predict")
                    .header("X-Internal-Api-Key", key).body(features).retrieve().body(Prediction.class);
            return valid(result) ? Optional.of(result) : Optional.empty();
        } catch (RestClientException ignored) {
            return Optional.empty();
        }
    }

    private static boolean valid(Prediction value) {
        if (value == null || !Boolean.TRUE.equals(value.validated()) || value.modelVersion() == null
                || value.modelVersion().isBlank() || !java.util.Set.of("POSITIVE", "CAUTION", "NEGATIVE").contains(value.outlook())
                || !java.util.Set.of("HIGH", "MEDIUM", "LOW").contains(value.confidence())) return false;
        return score(value.growthProbability()) && score(value.profitabilityImprovementProbability())
                && score(value.stabilityRiskProbability()) && value.expectedRevenueGrowth() != null
                && Double.isFinite(value.expectedRevenueGrowth());
    }

    private static boolean score(Double value) {
        return value != null && Double.isFinite(value) && value >= 0 && value <= 1;
    }
}

package com.jobpilot.api.domain.projectanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class GeminiProjectSummaryClient {
    private static final URI INTERACTIONS_URI = URI.create("https://generativelanguage.googleapis.com/v1beta/interactions");
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?im)((?:api[_-]?key|secret|password|token)\\s*[:=]\\s*[\\\"']?)[^\\s\\\"']+"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final boolean enabled;
    private final String model;

    GeminiProjectSummaryClient(
            ObjectMapper objectMapper,
            @Value("$" + "{GEMINI_API_KEY:}") String apiKey,
            @Value("$" + "{GEMINI_ENABLED:false}") boolean enabled,
            @Value("$" + "{GEMINI_MODEL:gemini-3.5-flash-lite}") String model
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.model = model;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    Optional<AiSummary> summarize(
            GitHubProjectAnalysisResponse analysis,
            GitHubRepositorySnapshot snapshot
    ) {
        if (!enabled || apiKey.isBlank()) return Optional.empty();
        try {
            HttpRequest request = HttpRequest.newBuilder(INTERACTIONS_URI)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequest(analysis, snapshot), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return Optional.empty();
            return parseResponse(response.body());
        } catch (IOException exception) {
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private String buildRequest(GitHubProjectAnalysisResponse analysis, GitHubRepositorySnapshot snapshot) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.put("system_instruction", """
                You summarize software repositories for a presentation preview.
                Use only the supplied evidence. Do not claim that code was run, tested, secure, complete, or production-ready.
                Write concise Korean prose. Preserve uncertainty when evidence is weak.
                """);
        request.put("input", prompt(analysis, snapshot));

        ObjectNode responseFormat = request.putObject("response_format");
        responseFormat.put("type", "text");
        responseFormat.put("mime_type", "application/json");
        responseFormat.set("schema", schema(analysis));
        return objectMapper.writeValueAsString(request);
    }

    private ObjectNode schema(GitHubProjectAnalysisResponse analysis) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ArrayNode required = root.putArray("required");
        required.add("overview");
        required.add("narrative");
        required.add("featureSummaries");

        ObjectNode properties = root.putObject("properties");
        properties.putObject("overview").put("type", "string");
        properties.putObject("narrative").put("type", "string");
        ObjectNode featureSummaries = properties.putObject("featureSummaries");
        featureSummaries.put("type", "object");
        ObjectNode featureProperties = featureSummaries.putObject("properties");
        ArrayNode featureRequired = featureSummaries.putArray("required");
        analysis.featureCandidates().forEach(candidate -> {
            featureProperties.putObject(candidate.id()).put("type", "string");
            featureRequired.add(candidate.id());
        });
        return root;
    }

    private String prompt(GitHubProjectAnalysisResponse analysis, GitHubRepositorySnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append("Repository: ").append(analysis.repository().fullName()).append('\n');
        builder.append("Existing deterministic overview: ").append(analysis.overview()).append("\n\n");
        builder.append("Candidate features and evidence:\n");
        analysis.featureCandidates().forEach(candidate -> builder
                .append("- id=").append(candidate.id()).append(", title=").append(candidate.title())
                .append(", files=").append(String.join(", ", candidate.evidence())).append('\n'));
        builder.append("\nSelected source excerpts (may be incomplete):\n");
        snapshot.analyzedFiles().stream().limit(12).forEach(file -> builder
                .append("\n--- ").append(file.path()).append(" ---\n")
                .append(sanitize(file.content(), 950)).append('\n'));
        builder.append("""

                Produce JSON matching the schema:
                - overview: one Korean sentence explaining the project from factual code evidence.
                - narrative: 2-3 Korean sentences suitable for a PPT generation brief.
                - featureSummaries: one short Korean sentence per supplied feature id, explaining its evidence without inventing behavior.
                """);
        return builder.toString();
    }

    private Optional<AiSummary> parseResponse(String body) {
        try {
            JsonNode response = objectMapper.readTree(body);
            for (JsonNode step : response.path("steps")) {
                if (!"model_output".equals(step.path("type").asText())) continue;
                for (JsonNode content : step.path("content")) {
                    String text = content.path("text").asText();
                    if (text.isBlank()) continue;
                    JsonNode summary = objectMapper.readTree(text);
                    Map<String, String> featureSummaries = new LinkedHashMap<>();
                    summary.path("featureSummaries").fields()
                            .forEachRemaining(entry -> featureSummaries.put(entry.getKey(), entry.getValue().asText()));
                    return Optional.of(new AiSummary(
                            summary.path("overview").asText(),
                            summary.path("narrative").asText(),
                            featureSummaries
                    ));
                }
            }
        } catch (IOException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private String sanitize(String content, int maxLength) {
        String redacted = SECRET_VALUE.matcher(content).replaceAll("$1<redacted>");
        return redacted.length() > maxLength ? redacted.substring(0, maxLength) + "\n..." : redacted;
    }

    record AiSummary(String overview, String narrative, Map<String, String> featureSummaries) {
    }
}

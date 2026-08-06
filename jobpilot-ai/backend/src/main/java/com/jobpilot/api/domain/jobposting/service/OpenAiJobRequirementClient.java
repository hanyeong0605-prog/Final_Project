package com.jobpilot.api.domain.jobposting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Calls GPT-5.6 Luna only for job-posting requirement extraction. */
@Component
class OpenAiJobRequirementClient {
    private static final URI CHAT_COMPLETIONS_URI = URI.create("https://api.openai.com/v1/chat/completions");
    private static final int MAX_DESCRIPTION_CHARS = 12_000;
    private static final int MAX_REQUIREMENTS = 15;
    private static final int MAX_REQUEST_ATTEMPTS = 3;
    private static final Duration TRANSIENT_RETRY_DELAY = Duration.ofSeconds(5);
    private static final Duration RATE_LIMIT_RETRY_DELAY = Duration.ofSeconds(65);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    OpenAiJobRequirementClient(
            ObjectMapper objectMapper,
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${JOB_REQUIREMENT_EXTRACTION_MODEL:gpt-5.6-luna}") String model
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    List<ExtractedJobRequirement> extract(JobPosting posting) {
        if (!isAvailable()) {
            throw new IllegalStateException("OpenAI requirement extraction is unavailable. Check OPENAI_API_KEY.");
        }
        if (posting.getDescription() == null || posting.getDescription().isBlank()) {
            return List.of();
        }

        try {
            String requestBody;
            try {
                requestBody = buildRequest(posting);
            } catch (IOException exception) {
                throw new IllegalStateException("OpenAI requirement extraction request could not be created.", exception);
            }

            HttpRequest request = HttpRequest.newBuilder(CHAT_COMPLETIONS_URI)
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            for (int attempt = 1; attempt <= MAX_REQUEST_ATTEMPTS; attempt++) {
                HttpResponse<String> response;
                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (IOException exception) {
                    if (attempt == MAX_REQUEST_ATTEMPTS) {
                        throw new IllegalStateException("OpenAI requirement extraction could not be completed.", exception);
                    }
                    waitBeforeRetry(TRANSIENT_RETRY_DELAY);
                    continue;
                }

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    try {
                        return parse(response.body());
                    } catch (IOException exception) {
                        throw new IllegalStateException(
                                "OpenAI requirement extraction returned unreadable JSON: "
                                        + clip(response.body().replaceAll("\\s+", " "), 500),
                                exception
                        );
                    }
                }
                if (isCreditExhausted(response)) {
                    throw new OpenAiQuotaExhaustedException("OpenAI API credit is exhausted or unavailable.");
                }
                if (isRetryable(response.statusCode()) && attempt < MAX_REQUEST_ATTEMPTS) {
                    waitBeforeRetry(response.statusCode() == 429
                            ? RATE_LIMIT_RETRY_DELAY
                            : TRANSIENT_RETRY_DELAY);
                    continue;
                }
                throw new IllegalStateException("OpenAI requirement extraction failed: HTTP " + response.statusCode());
            }
            throw new IllegalStateException("OpenAI requirement extraction exhausted all retry attempts.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI requirement extraction was interrupted.", exception);
        }
    }

    private boolean isCreditExhausted(HttpResponse<String> response) {
        if (response.statusCode() != 429) return false;
        String body = response.body().toLowerCase(Locale.ROOT);
        return body.contains("insufficient_quota") || body.contains("billing") || body.contains("credit balance");
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void waitBeforeRetry(Duration delay) throws InterruptedException {
        Thread.sleep(delay.toMillis());
    }

    private String buildRequest(JobPosting posting) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.put("max_completion_tokens", 2_048);
        request.put("reasoning_effort", "none");

        ArrayNode messages = request.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", """
                        You extract explicitly stated requirements from Korean IT job postings for a career-preparation service.
                        Do not infer, embellish, or include company culture, benefits, responsibilities, or vague marketing text.
                        Return one requirement per item. Use SKILL only for a concrete technology, language, framework, platform, or tool.
                        Use EXPERIENCE for explicit years/months or career-level conditions, EDUCATION for explicit degree/major conditions,
                        CERTIFICATION for explicit certificates, and OTHER only for another explicit eligibility condition.
                        importance is REQUIRED only when the source clearly says required, qualification, eligibility, or equivalent;
                        it is PREFERRED only when the source clearly says preferred, bonus, or advantage. Every item must include an exact,
                        short sourceExcerpt copied from the supplied posting. Keep each sourceExcerpt to the smallest phrase that proves
                        the condition, at most 120 characters. Return at most 15 distinct requirements. If a condition is ambiguous,
                        omit it rather than guessing.
                        """);
        messages.addObject().put("role", "user").put("content", prompt(posting));

        ObjectNode responseFormat = request.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", "job_requirements");
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", schema());
        return objectMapper.writeValueAsString(request);
    }

    private String prompt(JobPosting posting) {
        String description = posting.getDescription().trim();
        if (description.length() > MAX_DESCRIPTION_CHARS) {
            description = description.substring(0, MAX_DESCRIPTION_CHARS);
        }
        return """
                Job title: %s

                Job posting text:
                ---
                %s
                ---

                Extract only explicit requirements from this posting using the supplied JSON schema.
                """.formatted(blankToUnknown(posting.getTitle()), description);
    }

    private ObjectNode schema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.putArray("required").add("requirements");

        ObjectNode properties = root.putObject("properties");
        ObjectNode requirements = properties.putObject("requirements");
        requirements.put("type", "array");
        requirements.put("maxItems", MAX_REQUIREMENTS);
        ObjectNode item = requirements.putObject("items");
        item.put("type", "object");
        item.put("additionalProperties", false);
        item.putArray("required").add("type").add("content").add("importance").add("sourceExcerpt");
        ObjectNode itemProperties = item.putObject("properties");
        ArrayNode types = itemProperties.putObject("type").putArray("enum");
        types.add("SKILL").add("EXPERIENCE").add("EDUCATION").add("CERTIFICATION").add("OTHER");
        itemProperties.putObject("content").put("type", "string").put("maxLength", 300);
        ArrayNode importance = itemProperties.putObject("importance").putArray("enum");
        importance.add("REQUIRED").add("PREFERRED");
        itemProperties.putObject("sourceExcerpt").put("type", "string").put("maxLength", 120);
        return root;
    }

    private List<ExtractedJobRequirement> parse(String responseBody) throws IOException {
        JsonNode response = objectMapper.readTree(responseBody);
        String output = response.path("choices").path(0).path("message").path("content").asText();
        if (output.isBlank()) {
            throw new IllegalStateException("OpenAI requirement extraction returned no JSON content.");
        }

        JsonNode root = objectMapper.readTree(stripMarkdownCodeFence(output));
        if (!root.path("requirements").isArray()) {
            throw new IllegalStateException("OpenAI requirement extraction response does not contain requirements.");
        }

        Map<String, ExtractedJobRequirement> distinct = new LinkedHashMap<>();
        for (JsonNode item : root.path("requirements")) {
            ExtractedJobRequirement requirement = toRequirement(item);
            if (requirement == null) continue;
            String key = requirement.type() + "|" + requirement.importance() + "|"
                    + requirement.content().toLowerCase(Locale.ROOT);
            distinct.putIfAbsent(key, requirement);
            if (distinct.size() == MAX_REQUIREMENTS) break;
        }
        return List.copyOf(distinct.values());
    }

    private String stripMarkdownCodeFence(String output) {
        String trimmed = output.trim();
        if (!trimmed.startsWith("```")) return trimmed;

        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0) return trimmed;
        String withoutOpeningFence = trimmed.substring(firstLineEnd + 1).trim();
        return withoutOpeningFence.endsWith("```")
                ? withoutOpeningFence.substring(0, withoutOpeningFence.length() - 3).trim()
                : withoutOpeningFence;
    }

    private ExtractedJobRequirement toRequirement(JsonNode item) {
        String type = item.path("type").asText().trim();
        String content = item.path("content").asText().trim();
        String importance = item.path("importance").asText().trim();
        String sourceExcerpt = item.path("sourceExcerpt").asText().trim();
        if (!List.of("SKILL", "EXPERIENCE", "EDUCATION", "CERTIFICATION", "OTHER").contains(type)
                || !List.of("REQUIRED", "PREFERRED").contains(importance)
                || content.isBlank() || sourceExcerpt.isBlank()) {
            return null;
        }
        return new ExtractedJobRequirement(type, clip(content, 2_000), importance, clip(sourceExcerpt, 2_000));
    }

    boolean isAvailable() {
        return !apiKey.isBlank() && !model.isBlank();
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String clip(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

class OpenAiQuotaExhaustedException extends RuntimeException {
    OpenAiQuotaExhaustedException(String message) {
        super(message);
    }
}

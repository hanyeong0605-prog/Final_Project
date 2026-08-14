package com.jobpilot.api.domain.portfolio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse.ImplementationStory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// GitHubProjectAnalysisService가 쓰는 GeminiProjectSummaryClient와 같은 Gemini interactions
// API, 같은 안전장치(store=false, thinking_level=minimal, 출력 토큰 상한)를 쓰지만 목적이
// 다르다(코드 분석 설명이 아니라 발표 슬라이드 구조 생성)라 별도 클라이언트로 둔다. 이미
// 검증돼 배포된 GeminiProjectSummaryClient 파일은 건드리지 않는다.
@Component
class PortfolioNarrativeGeminiClient {
    private static final Logger log = LoggerFactory.getLogger(PortfolioNarrativeGeminiClient.class);
    private static final URI INTERACTIONS_URI = URI.create("https://generativelanguage.googleapis.com/v1beta/interactions");
    private static final int MAX_OUTPUT_TOKENS = 1_100;
    private static final int MAX_SLIDES = 6;

    private final ObjectMapper objectMapper;
    private final ObjectMapper lenientJsonMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final boolean enabled;
    private final String model;

    PortfolioNarrativeGeminiClient(
            ObjectMapper objectMapper,
            @Value("$" + "{GEMINI_API_KEY:}") String apiKey,
            @Value("$" + "{GEMINI_ENABLED:false}") boolean enabled,
            @Value("$" + "{GEMINI_ANALYSIS_MODEL:gemini-3.5-flash}") String model
    ) {
        this.objectMapper = objectMapper;
        this.lenientJsonMapper = objectMapper.copy()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature())
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature())
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature());
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.model = model;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    NarrativeResult generate(GitHubProjectAnalysisResponse analysis, List<ImplementationStory> selected) {
        if (!isAvailable()) return NarrativeResult.notRequested();
        try {
            HttpRequest request = HttpRequest.newBuilder(INTERACTIONS_URI)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequest(analysis, selected), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (!isSuccess(response.statusCode())) {
                log.warn("Gemini portfolio-narrative request failed: HTTP {}", response.statusCode());
                return NarrativeResult.failed();
            }
            Optional<PortfolioNarrative> narrative = parseResponse(response.body(), selected);
            if (narrative.isEmpty()) {
                log.warn("Gemini portfolio-narrative response did not contain a valid slide structure.");
            }
            return narrative.map(NarrativeResult::success).orElseGet(NarrativeResult::failed);
        } catch (IOException exception) {
            log.warn("Gemini portfolio-narrative request could not be completed: {}", exception.getClass().getSimpleName());
            return NarrativeResult.failed();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Gemini portfolio-narrative request was interrupted.");
            return NarrativeResult.failed();
        }
    }

    private String buildRequest(GitHubProjectAnalysisResponse analysis, List<ImplementationStory> selected) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.put("store", false);
        request.put("system_instruction", """
                You are preparing Korean presentation slide content for a developer's portfolio talk.
                Use only the supplied project facts and the selected implementation stories; do not invent
                features, metrics, or outcomes that are not present in the input. Do not provide code review,
                recommendations, or subjective quality judgments. Each slide must stay grounded in the supplied
                evidence paths. Write concise, confident Korean prose suitable for reading aloud in an interview.
                """);
        request.put("input", prompt(analysis, selected));
        ObjectNode generationConfig = request.putObject("generation_config");
        generationConfig.put("max_output_tokens", MAX_OUTPUT_TOKENS);
        generationConfig.put("thinking_level", "minimal");
        generationConfig.put("temperature", 0.2);
        ObjectNode responseFormat = request.putObject("response_format");
        responseFormat.put("type", "text");
        responseFormat.put("mime_type", "application/json");
        responseFormat.set("schema", buildSchema());
        return objectMapper.writeValueAsString(request);
    }

    private ObjectNode buildSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putArray("required").add("titleSlide").add("slides");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode titleSlide = properties.putObject("titleSlide");
        titleSlide.put("type", "object");
        ObjectNode titleSlideProps = titleSlide.putObject("properties");
        titleSlideProps.putObject("title").put("type", "string");
        titleSlideProps.putObject("subtitle").put("type", "string");

        ObjectNode slides = properties.putObject("slides");
        slides.put("type", "array");
        ObjectNode slideItems = slides.putObject("items");
        slideItems.put("type", "object");
        ObjectNode slideProps = slideItems.putObject("properties");
        slideProps.putObject("heading").put("type", "string");
        ObjectNode bullets = slideProps.putObject("bullets");
        bullets.put("type", "array");
        bullets.putObject("items").put("type", "string");
        slideProps.putObject("speakerNote").put("type", "string");
        ObjectNode evidencePaths = slideProps.putObject("evidencePaths");
        evidencePaths.put("type", "array");
        evidencePaths.putObject("items").put("type", "string");
        return schema;
    }

    private String prompt(GitHubProjectAnalysisResponse analysis, List<ImplementationStory> selected) {
        StringBuilder builder = new StringBuilder();
        builder.append("Repository: ").append(analysis.repository().fullName()).append('\n');
        builder.append("Project classification: ")
                .append(analysis.projectProfile() != null ? analysis.projectProfile().classification() : "")
                .append('\n');
        builder.append("Overview: ").append(analysis.overview()).append("\n\n");
        builder.append("Selected implementations to build slides from (build one slide per implementation, in this order):\n");
        for (ImplementationStory story : selected) {
            builder.append("\n- Title: ").append(story.title());
            builder.append("\n  Description: ").append(story.description());
            builder.append("\n  Mechanism: ").append(story.mechanism());
            builder.append("\n  Technologies: ").append(story.technologies());
            builder.append("\n  Evidence paths: ");
            story.evidence().forEach(evidence -> builder.append(evidence.path()).append(' '));
        }
        builder.append("\n\nReturn only JSON matching the schema. Produce at most ")
                .append(MAX_SLIDES)
                .append(" content slides, one per selected implementation, plus one titleSlide object. ")
                .append("Each slide's evidencePaths must be a subset of that implementation's evidence paths.\n");
        return builder.toString();
    }

    // Gemini는 evidencePaths로 파일 경로 문자열만 돌려준다(스키마를 안 바꿨다 - AI 응답
    // 형식을 건드리는 건 이 세션에서 검증할 방법이 없어 리스크가 크다고 판단). 대신 이미
    // 우리가 갖고 있는 selected(구현별 evidence: path+symbol)에서 경로별 symbol을 먼저
    // 모아두고, Gemini가 고른 경로에 그 symbol을 다시 붙여서 EvidenceRef를 만든다 - 이렇게
    // 하면 "한 파일이 여러 구현의 근거로 쓰일 때 항상 같은 코드만 보여준다"는 문제
    // (2026-08-14)를 Gemini 쪽 응답 포맷은 그대로 두고 고칠 수 있다.
    private Optional<PortfolioNarrative> parseResponse(String body, List<ImplementationStory> selected) {
        Map<String, String> symbolByPath = new LinkedHashMap<>();
        for (ImplementationStory story : selected) {
            for (GitHubProjectAnalysisResponse.CodeEvidence evidence : story.evidence()) {
                symbolByPath.putIfAbsent(evidence.path(), evidence.symbol());
            }
        }
        try {
            JsonNode response = objectMapper.readTree(body);
            for (String text : outputTexts(response)) {
                JsonNode narrative = jsonObject(text).orElse(null);
                if (narrative == null) continue;
                JsonNode titleSlideNode = narrative.path("titleSlide");
                String title = titleSlideNode.path("title").asText();
                String subtitle = titleSlideNode.path("subtitle").asText();
                if (title.isBlank()) continue;
                List<PortfolioSlide> slides = new ArrayList<>();
                for (JsonNode slideNode : narrative.path("slides")) {
                    List<String> bullets = new ArrayList<>();
                    slideNode.path("bullets").forEach(bullet -> bullets.add(bullet.asText()));
                    List<EvidenceRef> evidence = new ArrayList<>();
                    slideNode.path("evidencePaths").forEach(pathNode -> {
                        String path = pathNode.asText();
                        evidence.add(new EvidenceRef(path, symbolByPath.get(path)));
                    });
                    String heading = slideNode.path("heading").asText();
                    if (heading.isBlank() || bullets.isEmpty()) continue;
                    slides.add(new PortfolioSlide(heading, bullets, slideNode.path("speakerNote").asText(), evidence));
                }
                if (slides.isEmpty()) continue;
                return Optional.of(new PortfolioNarrative(title, subtitle, slides));
            }
        } catch (IOException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<JsonNode> jsonObject(String text) {
        try {
            JsonNode direct = lenientJsonMapper.readTree(text.trim());
            if (direct.isObject()) return Optional.of(direct);
        } catch (IOException ignored) {
            // A fenced JSON response is still safe to read after extracting its object portion.
        }
        int opening = text.indexOf('{');
        int closing = text.lastIndexOf('}');
        if (opening < 0 || closing <= opening) return Optional.empty();
        try {
            JsonNode extracted = lenientJsonMapper.readTree(text.substring(opening, closing + 1));
            return extracted.isObject() ? Optional.of(extracted) : Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private List<String> outputTexts(JsonNode response) {
        List<String> texts = new ArrayList<>();
        String directText = response.path("output_text").asText();
        if (!directText.isBlank()) texts.add(directText);
        for (JsonNode step : response.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) continue;
            for (JsonNode content : step.path("content")) {
                String text = content.path("text").asText();
                if (!text.isBlank()) texts.add(text);
            }
        }
        return texts;
    }

    private boolean isAvailable() { return enabled && !apiKey.isBlank(); }

    private boolean isSuccess(int statusCode) { return statusCode >= 200 && statusCode < 300; }

    record EvidenceRef(String path, String symbol) {
    }

    record PortfolioSlide(String heading, List<String> bullets, String speakerNote, List<EvidenceRef> evidence) {
    }

    record PortfolioNarrative(String title, String subtitle, List<PortfolioSlide> slides) {
    }

    record NarrativeResult(Optional<PortfolioNarrative> narrative, boolean requested) {
        static NarrativeResult notRequested() { return new NarrativeResult(Optional.empty(), false); }
        static NarrativeResult failed() { return new NarrativeResult(Optional.empty(), true); }
        static NarrativeResult success(PortfolioNarrative narrative) { return new NarrativeResult(Optional.of(narrative), true); }
    }
}

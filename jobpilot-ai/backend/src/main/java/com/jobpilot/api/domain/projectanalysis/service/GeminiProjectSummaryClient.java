package com.jobpilot.api.domain.projectanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class GeminiProjectSummaryClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiProjectSummaryClient.class);
    private static final URI INTERACTIONS_URI = URI.create("https://generativelanguage.googleapis.com/v1beta/interactions");
    private static final int MAX_CODE_FILES = 8;
    private static final int MAX_CONTEXT_FILES = 12;
    private static final int MAX_CODE_CHARS_PER_FILE = 3_500;
    private static final int MAX_CONFIG_CHARS_PER_FILE = 1_600;
    private static final int MAX_OUTPUT_TOKENS = 1_200;
    private static final int MAX_PLAN_OUTPUT_TOKENS = 360;
    private static final int MAX_PLAN_TREE_PATHS = 400;
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?im)((?:api[_-]?key|secret|password|token)\\s*[:=]\\s*[\\\"']?)[^\\s\\\"']+"
    );

    private final ObjectMapper objectMapper;
    private final ObjectMapper lenientJsonMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final boolean enabled;
    private final String planningModel;
    private final String analysisModel;

    GeminiProjectSummaryClient(
            ObjectMapper objectMapper,
            @Value("$" + "{GEMINI_API_KEY:}") String apiKey,
            @Value("$" + "{GEMINI_ENABLED:false}") boolean enabled,
            @Value("$" + "{GEMINI_MODEL:gemini-3.5-flash-lite}") String planningModel,
            @Value("$" + "{GEMINI_ANALYSIS_MODEL:gemini-3.5-flash}") String analysisModel
    ) {
        this.objectMapper = objectMapper;
        this.lenientJsonMapper = objectMapper.copy()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature())
                .enable(JsonReadFeature.ALLOW_YAML_COMMENTS.mappedFeature())
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature())
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature())
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature());
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.planningModel = planningModel;
        this.analysisModel = analysisModel;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    Optional<CodeReadingPlan> planCodeReading(GitHubRepositorySnapshot snapshot) {
        if (!isAvailable()) return Optional.empty();
        try {
            HttpRequest request = HttpRequest.newBuilder(INTERACTIONS_URI)
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(buildPlanningRequest(snapshot), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (!isSuccess(response.statusCode())) {
                log.warn("Gemini code-reading plan request failed: HTTP {}", response.statusCode());
                return Optional.empty();
            }
            Optional<CodeReadingPlan> plan = parseCodeReadingPlan(response.body());
            if (plan.isEmpty() || plan.get().focusPaths().isEmpty()) {
                log.warn("Gemini code-reading plan contained no usable source paths.");
            }
            return plan;
        } catch (IOException exception) {
            log.warn("Gemini code-reading plan request could not be completed: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Gemini code-reading plan request was interrupted.");
            return Optional.empty();
        }
    }

    GeminiSummaryResult summarize(
            GitHubProjectAnalysisResponse analysis,
            GitHubRepositorySnapshot snapshot,
            List<String> focusPaths
    ) {
        if (!isAvailable()) return GeminiSummaryResult.notRequested();
        try {
            HttpRequest request = HttpRequest.newBuilder(INTERACTIONS_URI)
                    .timeout(Duration.ofSeconds(35))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequest(analysis, snapshot, focusPaths), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (!isSuccess(response.statusCode())) {
                log.warn("Gemini project-summary request failed: HTTP {}", response.statusCode());
                return GeminiSummaryResult.failed();
            }
            String generatedOutput = generatedOutput(response.body());
            Optional<AiSummary> summary = parseResponse(response.body());
            if (summary.isEmpty()) {
                log.warn("Gemini project-summary response did not contain a valid structured explanation: {}",
                        responseDiagnostic(response.body()));
            }
            return summary.map(GeminiSummaryResult::success)
                    .orElseGet(() -> GeminiSummaryResult.failed(generatedOutput));
        } catch (IOException exception) {
            log.warn("Gemini project-summary request could not be completed: {}", exception.getClass().getSimpleName());
            return GeminiSummaryResult.failed();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Gemini project-summary request was interrupted.");
            return GeminiSummaryResult.failed();
        }
    }

    private String buildRequest(
            GitHubProjectAnalysisResponse analysis,
            GitHubRepositorySnapshot snapshot,
            List<String> focusPaths
    ) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", analysisModel);
        request.put("store", false);
        request.put("system_instruction", """
                You are a senior software engineer preparing a Korean code-analysis preview.
                Read the supplied code first; a README is optional context, never a requirement.
                State only facts supported by supplied files. Do not claim execution, tests, security, completeness,
                production readiness, API success, database contents, or behavior that is not visible in code.
                Every flow must cite exact supplied source paths. If evidence is weak, say so instead of guessing.
                Avoid generic reasons such as 'it has main()'. Explain concrete responsibility and relationships visible in code.
                Do not provide code review, recommendations, shortcomings, risks, or subjective evaluation.
                Write concise Korean prose for a developer who will decide what belongs in a presentation.
                """);
        request.put("input", prompt(analysis, snapshot, focusPaths));

        ObjectNode generationConfig = request.putObject("generation_config");
        generationConfig.put("max_output_tokens", MAX_OUTPUT_TOKENS);
        generationConfig.put("thinking_level", "minimal");
        generationConfig.put("temperature", 0.1);

        return objectMapper.writeValueAsString(request);
    }

    private String buildPlanningRequest(GitHubRepositorySnapshot snapshot) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", planningModel);
        request.put("store", false);
        request.put("system_instruction", """
                You are planning a bounded software-repository reading pass.
                Select source files that explain the repository's architecture and important user-visible implementations.
                Use only exact paths supplied in the repository tree. Do not summarize, review, recommend, or evaluate code.
                Prefer public API or entry files, core orchestration, data/communication abstractions, and environment adapters.
                When the repository contains tutorials or many independent examples, first choose one coherent example module:
                its entry point plus related parent/child classes, interfaces, or collaborators in the same package.
                Do not spend all selections on unrelated files that merely contain main().
                A README may be absent or incomplete; use it only as supplemental context.
                """);
        request.put("input", planningPrompt(snapshot));
        ObjectNode generationConfig = request.putObject("generation_config");
        generationConfig.put("max_output_tokens", MAX_PLAN_OUTPUT_TOKENS);
        generationConfig.put("thinking_level", "minimal");
        ObjectNode responseFormat = jsonResponseFormat(request);
        responseFormat.put("type", "text");
        responseFormat.put("mime_type", "application/json");
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putArray("required").add("focusPaths");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode focusPaths = properties.putObject("focusPaths");
        focusPaths.put("type", "array");
        focusPaths.putObject("items").put("type", "string");
        responseFormat.set("schema", schema);
        return objectMapper.writeValueAsString(request);
    }

    private String planningPrompt(GitHubRepositorySnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append("Repository: ").append(snapshot.reference().owner()).append('/').append(snapshot.reference().repository()).append('\n');
        builder.append("Description: ").append(snapshot.description()).append('\n');
        builder.append("Languages: ").append(snapshot.languageBytes().keySet()).append("\n\n");
        builder.append("Repository paths (select at most 8 source-code paths from this list):\n");
        planningPaths(snapshot).forEach(path -> builder.append("- ").append(path).append('\n'));
        builder.append("\nInitially inspected context files:\n");
        snapshot.analyzedFiles().stream()
                .filter(file -> !isCodeFile(file.path()))
                .forEach(file -> builder.append("\n--- ").append(file.path()).append(" ---\n")
                        .append(sanitize(file.content(), MAX_CONFIG_CHARS_PER_FILE)).append('\n'));
        builder.append("\nInitial source samples (these are not automatically the most important files):\n");
        snapshot.analyzedFiles().stream()
                .filter(file -> isCodeFile(file.path()))
                .limit(6)
                .forEach(file -> builder.append("\n--- ").append(file.path()).append(" ---\n")
                        .append(sanitize(file.content(), 650)).append('\n'));
        builder.append("\nReturn only JSON with focusPaths. Select 2 to 8 paths; prefer a coherent related-code cluster over unrelated main() examples. Do not choose tests, generated files, or environment secrets.\n");
        return builder.toString();
    }

    private List<String> planningPaths(GitHubRepositorySnapshot snapshot) {
        List<String> sourcePaths = snapshot.allFiles().stream()
                .filter(file -> isCodeFile(file.path()))
                .map(GitHubRepositorySnapshot.RepositoryFile::path)
                .toList();
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        sourcePaths.stream()
                .sorted(java.util.Comparator.comparingInt(this::planningPathScore).reversed().thenComparing(path -> path))
                .limit(MAX_PLAN_TREE_PATHS / 2)
                .forEach(paths::add);
        Set<String> directories = new LinkedHashSet<>();
        sourcePaths.stream().sorted().forEach(path -> {
            String directory = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : ".";
            if (directories.add(directory) && paths.size() < MAX_PLAN_TREE_PATHS) paths.add(path);
        });
        sourcePaths.stream().sorted().limit(MAX_PLAN_TREE_PATHS).forEach(paths::add);
        return paths.stream().limit(MAX_PLAN_TREE_PATHS).toList();
    }

    private int planningPathScore(String path) {
        String value = path.toLowerCase();
        int score = 0;
        if (value.contains("/api/") || value.contains("controller") || value.contains("router")) score += 8;
        if (value.contains("service") || value.contains("handler") || value.contains("bridge") || value.contains("communication")) score += 7;
        if (value.contains("parser") || value.contains("repository") || value.contains("client") || value.contains("gateway")) score += 6;
        if (value.contains("walker") || value.contains("core") || value.contains("main") || value.contains("app.")) score += 5;
        if (value.contains("adapter") || value.contains("widget") || value.contains("streamlit") || value.contains("jupyter")) score += 4;
        return score;
    }

    private ObjectNode schema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ArrayNode required = root.putArray("required");
        required.add("overview");
        required.add("narrative");
        required.add("projectClassification");
        required.add("projectSummary");
        required.add("files");
        required.add("flows");
        required.add("implementations");

        ObjectNode properties = root.putObject("properties");
        properties.putObject("overview").put("type", "string");
        properties.putObject("narrative").put("type", "string");
        properties.putObject("projectClassification").put("type", "string");
        properties.putObject("projectSummary").put("type", "string");

        ObjectNode files = properties.putObject("files");
        files.put("type", "array");
        files.set("items", fileExplanationSchema());

        ObjectNode flows = properties.putObject("flows");
        flows.put("type", "array");
        flows.set("items", flowSchema());

        ObjectNode implementations = properties.putObject("implementations");
        implementations.put("type", "array");
        implementations.set("items", implementationSchema());

        return root;
    }

    private ObjectNode fileExplanationSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putArray("required").add("path").add("responsibility").add("selectionReason").add("importance");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("path").put("type", "string");
        properties.putObject("responsibility").put("type", "string");
        properties.putObject("selectionReason").put("type", "string");
        properties.putObject("importance").put("type", "string");
        return schema;
    }

    private ObjectNode flowSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putArray("required").add("title").add("description").add("evidence").add("confidence");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("title").put("type", "string");
        properties.putObject("description").put("type", "string");
        properties.putObject("confidence").put("type", "string");
        ObjectNode evidence = properties.putObject("evidence");
        evidence.put("type", "array");
        evidence.putObject("items").put("type", "string");
        return schema;
    }

    private ObjectNode implementationSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putArray("required").add("title").add("description").add("mechanism").add("technologies").add("evidence");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("title").put("type", "string");
        properties.putObject("description").put("type", "string");
        properties.putObject("mechanism").put("type", "string");
        ObjectNode technologies = properties.putObject("technologies");
        technologies.put("type", "array");
        technologies.putObject("items").put("type", "string");
        ObjectNode evidence = properties.putObject("evidence");
        evidence.put("type", "array");
        evidence.set("items", codeEvidenceSchema());
        return schema;
    }

    private ObjectNode codeEvidenceSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putArray("required").add("path").add("symbol").add("description");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("path").put("type", "string");
        properties.putObject("symbol").put("type", "string");
        properties.putObject("description").put("type", "string");
        return schema;
    }

    private String prompt(
            GitHubProjectAnalysisResponse analysis,
            GitHubRepositorySnapshot snapshot,
            List<String> focusPaths
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Repository: ").append(analysis.repository().fullName()).append('\n');
        builder.append("GitHub description (may be empty): ").append(analysis.repository().description()).append("\n");
        builder.append("Static tentative classification: ").append(analysis.projectProfile().classification()).append("\n\n");
        builder.append("Detected technologies; use only these names when referring to a technology:\n");
        analysis.technologyStack().forEach(technology -> builder
                .append("- ").append(technology.name()).append(" (evidence: ")
                .append(String.join(", ", technology.evidence())).append(")\n"));
        builder.append('\n');
        builder.append("Core files to explain. Do not infer a business feature from a filename alone:\n");
        analysis.coreFiles().forEach(file -> builder
                .append("- path=").append(file.path()).append(", staticRole=").append(file.role())
                .append(", staticReason=").append(file.selectionReason()).append('\n'));
        builder.append("\nStatic feature candidates are secondary evidence only. Do not use a filename alone to decide an implementation story:\n");
        analysis.featureCandidates().forEach(candidate -> builder
                .append("- id=").append(candidate.id()).append(", title=").append(candidate.title())
                .append(", evidence=").append(String.join(", ", candidate.evidence())).append('\n'));
        builder.append("\nSupplied code and configuration evidence:\n");
        semanticFiles(analysis, snapshot, focusPaths).forEach(file -> {
            int maxLength = isCodeFile(file.path()) ? MAX_CODE_CHARS_PER_FILE : MAX_CONFIG_CHARS_PER_FILE;
            builder.append("\n--- PATH: ").append(file.path()).append(" ---\n")
                    .append(sanitize(file.content(), maxLength)).append('\n');
        });
        builder.append("""

                Return exactly one minified, single-line JSON object and no Markdown or prose outside that JSON. Use this shape:
                {
                  "overview": "...",
                  "narrative": "...",
                  "projectClassification": "...",
                  "projectSummary": "...",
                  "files": [{"path": "exact supplied path", "responsibility": "...", "selectionReason": "...", "importance": "CORE|STRUCTURAL|REFERENCE"}],
                  "flows": [{"title": "...", "description": "...", "evidence": ["exact supplied path"], "confidence": "HIGH|MEDIUM|LOW"}],
                  "implementations": [{"title": "...", "description": "...", "mechanism": "...", "technologies": ["technology named above"], "evidence": [{"path": "exact supplied path", "symbol": "class, method, or function name", "description": "..."}]}]
                }
                - overview: one factual Korean sentence describing the repository, under 45 Korean characters.
                - narrative: one factual Korean sentence that gives a PPT author a defensible story, under 80 Korean characters.
                - projectClassification: code-first Korean label under 35 Korean characters. projectSummary: code-first Korean explanation under 100 Korean characters.
                  If the repository is a learning/example collection rather than one product, explicitly say so and describe the visible package or chapter progression.
                - files: return exactly two listed core paths. For each, explain concrete responsibility and code-based selection reason,
                  each under 55 Korean characters.
                  Use only exact supplied paths. Use CORE only when the code is central to a visible request, domain, integration, or application flow;
                  use STRUCTURAL for an entry point or structure-supporting file; use REFERENCE when evidence is weak.
                - flows: return exactly one visible code/data/control flow. Its description is under 90 Korean characters and it cites up to two exact core paths.
                - implementations: return exactly one factual, user-facing implementation story. Its description is under 90 Korean characters,
                  mechanism under 75 Korean characters, technologies has at most two items, and evidence has exactly one exact core-file item
                  with its description under 55 Korean characters.
                  When supplied files show inheritance, interface implementation, or an entry-point-to-collaborator relationship, explain that relationship;
                  never select a file merely because it contains main().
                  Do not turn independent example classes into an invented product feature. Do not include advice or evaluation.
                """);
        return builder.toString();
    }

    private List<GitHubRepositorySnapshot.RepositoryFile> semanticFiles(
            GitHubProjectAnalysisResponse analysis,
            GitHubRepositorySnapshot snapshot,
            List<String> focusPaths
    ) {
        Map<String, GitHubRepositorySnapshot.RepositoryFile> byPath = new LinkedHashMap<>();
        snapshot.analyzedFiles().forEach(file -> byPath.put(file.path(), file));
        Set<String> selectedPaths = new LinkedHashSet<>();
        focusPaths.forEach(selectedPaths::add);
        analysis.coreFiles().stream().map(GitHubProjectAnalysisResponse.CoreFile::path).forEach(selectedPaths::add);
        snapshot.analyzedFiles().stream()
                .filter(file -> isContextFile(file.path()))
                .map(GitHubRepositorySnapshot.RepositoryFile::path)
                .forEach(selectedPaths::add);
        return selectedPaths.stream()
                .map(byPath::get)
                .filter(java.util.Objects::nonNull)
                .limit(MAX_CONTEXT_FILES)
                .toList();
    }

    private boolean isCodeFile(String path) {
        return path.matches(".*\\.(java|kt|ts|tsx|js|jsx|py|go|rb|cs|php)$");
    }

    private boolean isContextFile(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        return filename.equals("pom.xml") || filename.equals("package.json") || filename.startsWith("readme")
                || filename.equals("application.yml") || filename.equals("application.yaml");
    }

    private Optional<CodeReadingPlan> parseCodeReadingPlan(String body) {
        try {
            JsonNode response = objectMapper.readTree(body);
            for (String text : outputTexts(response)) {
                JsonNode plan = objectMapper.readTree(text);
                List<String> paths = new ArrayList<>();
                plan.path("focusPaths").forEach(path -> paths.add(path.asText()));
                List<String> selected = paths.stream()
                        .filter(path -> !path.isBlank()).distinct().limit(MAX_CODE_FILES).toList();
                if (!selected.isEmpty()) return Optional.of(new CodeReadingPlan(selected));
            }
        } catch (IOException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<AiSummary> parseResponse(String body) {
        try {
            JsonNode response = objectMapper.readTree(body);
            for (String text : outputTexts(response)) {
                JsonNode summary = jsonObject(text).orElse(null);
                if (summary == null) continue;
                if (summary.path("overview").asText().isBlank() || summary.path("projectSummary").asText().isBlank()) continue;
                return Optional.of(new AiSummary(
                        summary.path("overview").asText(),
                        summary.path("narrative").asText(),
                        summary.path("projectClassification").asText(),
                        summary.path("projectSummary").asText(),
                        fileExplanations(summary.path("files")),
                        flows(summary.path("flows")),
                        implementations(summary.path("implementations")),
                        stringMap(summary.path("featureSummaries"))
                ));
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
        if (opening < 0 || closing <= opening) {
            log.warn("Gemini summary text did not include a JSON object boundary.");
            return Optional.empty();
        }
        try {
            JsonNode extracted = lenientJsonMapper.readTree(text.substring(opening, closing + 1));
            return extracted.isObject() ? Optional.of(extracted) : Optional.empty();
        } catch (IOException exception) {
            log.warn("Gemini summary JSON parse failed: {}", jsonErrorSummary(exception));
            return Optional.empty();
        }
    }

    private String jsonErrorSummary(IOException exception) {
        if (exception instanceof JsonProcessingException processingException) {
            return processingException.getOriginalMessage();
        }
        return exception.getClass().getSimpleName();
    }

    static ObjectNode jsonResponseFormat(ObjectNode request) {
        // Interactions REST API expects one response-format object, not an array of parts.
        return request.putObject("response_format");
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

    private String generatedOutput(String responseBody) {
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            String output = String.join("\n\n", outputTexts(response));
            return output.isBlank() ? null : sanitize(output, 6_000);
        } catch (IOException ignored) {
            return null;
        }
    }

    private String responseDiagnostic(String body) {
        try {
            JsonNode response = objectMapper.readTree(body);
            List<String> steps = new ArrayList<>();
            for (JsonNode step : response.path("steps")) {
                steps.add(step.path("type").asText("unknown") + ":" + step.path("status").asText("unknown"));
            }
            List<String> texts = outputTexts(response);
            int characters = texts.stream().mapToInt(String::length).sum();
            boolean hasJsonBounds = texts.stream().anyMatch(text -> text.indexOf('{') >= 0 && text.lastIndexOf('}') > text.indexOf('{'));
            return "steps=" + steps + ", textBlocks=" + texts.size() + ", characters=" + characters + ", jsonBounds=" + hasJsonBounds;
        } catch (IOException ignored) {
            return "response-body-unreadable";
        }
    }

    private boolean isAvailable() {
        return enabled && !apiKey.isBlank();
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private Map<String, FileExplanation> fileExplanations(JsonNode node) {
        Map<String, FileExplanation> result = new LinkedHashMap<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String path = item.path("path").asText();
                if (path.isBlank()) continue;
                result.put(path, new FileExplanation(
                        item.path("responsibility").asText(),
                        item.path("selectionReason").asText(),
                        item.path("importance").asText()
                ));
            }
            return result;
        }
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), new FileExplanation(
                entry.getValue().path("responsibility").asText(),
                entry.getValue().path("selectionReason").asText(),
                entry.getValue().path("importance").asText()
        )));
        return result;
    }

    private List<AiCodeFlow> flows(JsonNode node) {
        List<AiCodeFlow> result = new ArrayList<>();
        for (JsonNode flow : node) {
            List<String> evidence = new ArrayList<>();
            flow.path("evidence").forEach(path -> evidence.add(path.asText()));
            result.add(new AiCodeFlow(
                    flow.path("title").asText(), flow.path("description").asText(), evidence, flow.path("confidence").asText()
            ));
        }
        return result;
    }

    private List<AiImplementation> implementations(JsonNode node) {
        List<AiImplementation> result = new ArrayList<>();
        for (JsonNode implementation : node) {
            List<String> technologies = new ArrayList<>();
            implementation.path("technologies").forEach(technology -> technologies.add(technology.asText()));
            List<AiCodeEvidence> evidence = new ArrayList<>();
            implementation.path("evidence").forEach(item -> evidence.add(new AiCodeEvidence(
                    item.path("path").asText(), item.path("symbol").asText(), item.path("description").asText()
            )));
            result.add(new AiImplementation(
                    implementation.path("title").asText(), implementation.path("description").asText(),
                    implementation.path("mechanism").asText(), technologies, evidence
            ));
        }
        return result;
    }

    private Map<String, String> stringMap(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return result;
    }

    private String sanitize(String content, int maxLength) {
        String redacted = SECRET_VALUE.matcher(content).replaceAll("$1<redacted>");
        return redacted.length() > maxLength ? redacted.substring(0, maxLength) + "\n..." : redacted;
    }

    record AiSummary(
            String overview,
            String narrative,
            String projectClassification,
            String projectSummary,
            Map<String, FileExplanation> fileExplanations,
            List<AiCodeFlow> flows,
            List<AiImplementation> implementations,
            Map<String, String> featureSummaries
    ) {
    }

    record FileExplanation(String responsibility, String selectionReason, String importance) {
    }

    record AiCodeFlow(String title, String description, List<String> evidence, String confidence) {
    }

    record AiImplementation(
            String title,
            String description,
            String mechanism,
            List<String> technologies,
            List<AiCodeEvidence> evidence
    ) {
    }

    record AiCodeEvidence(String path, String symbol, String description) {
    }

    record CodeReadingPlan(List<String> focusPaths) {
    }

    record GeminiSummaryResult(Optional<AiSummary> summary, boolean requested, String generatedOutput) {
        static GeminiSummaryResult notRequested() {
            return new GeminiSummaryResult(Optional.empty(), false, null);
        }

        static GeminiSummaryResult failed() {
            return failed(null);
        }

        static GeminiSummaryResult failed(String generatedOutput) {
            return new GeminiSummaryResult(Optional.empty(), true, generatedOutput);
        }

        static GeminiSummaryResult success(AiSummary summary) {
            return new GeminiSummaryResult(Optional.of(summary), true, null);
        }
    }
}

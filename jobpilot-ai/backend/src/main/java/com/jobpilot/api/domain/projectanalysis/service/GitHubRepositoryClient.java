package com.jobpilot.api.domain.projectanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.projectanalysis.exception.ProjectAnalysisException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class GitHubRepositoryClient {
    private static final int MAX_ANALYZED_SOURCE_FILES = 10;
    private static final int MAX_ADDITIONAL_FOCUS_FILES = 12;
    private static final int MAX_RELATED_FOCUS_FILES = 4;
    private static final int MAX_FILE_BYTES = 120_000;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "kt", "ts", "tsx", "js", "jsx", "py", "go", "rb", "cs", "php", "sql", "xml", "yml", "yaml", "json", "md"
    );
    private static final Set<String> PRIMARY_FILENAMES = Set.of(
            "readme.md", "package.json", "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
            "requirements.txt", "pyproject.toml", "dockerfile", "docker-compose.yml"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String githubToken;

    GitHubRepositoryClient(ObjectMapper objectMapper, @Value("$" + "{GITHUB_TOKEN:}") String githubToken) {
        this.objectMapper = objectMapper;
        this.githubToken = githubToken;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    GitHubRepositorySnapshot load(String repositoryUrl) {
        GitHubRepositoryReference reference = GitHubRepositoryReference.parse(repositoryUrl);
        JsonNode repository = getJson("/repos/" + reference.owner() + "/" + reference.repository());
        String defaultBranch = GitHubRepositorySnapshot.text(repository, "default_branch");
        if (defaultBranch.isBlank()) throw new ProjectAnalysisException("The repository has no default branch.");

        Map<String, Integer> languageBytes = readLanguages(reference);
        List<GitHubRepositorySnapshot.RepositoryFile> allFiles = readTree(reference, defaultBranch);
        List<GitHubRepositorySnapshot.RepositoryFile> analyzedFiles = readSelectedFiles(reference, defaultBranch, allFiles);
        return new GitHubRepositorySnapshot(
                reference, defaultBranch, GitHubRepositorySnapshot.text(repository, "description"),
                languageBytes, allFiles, analyzedFiles
        );
    }

    GitHubRepositorySnapshot enrichWithFocusFiles(
            GitHubRepositorySnapshot snapshot,
            List<String> requestedPaths
    ) {
        Map<String, GitHubRepositorySnapshot.RepositoryFile> filesByPath = snapshot.allFiles().stream()
                .collect(java.util.stream.Collectors.toMap(
                        GitHubRepositorySnapshot.RepositoryFile::path, file -> file, (left, right) -> left, LinkedHashMap::new
                ));
        List<GitHubRepositorySnapshot.RepositoryFile> enriched = new ArrayList<>(snapshot.analyzedFiles());
        Set<String> includedPaths = enriched.stream()
                .map(GitHubRepositorySnapshot.RepositoryFile::path)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String requestedPath : requestedPaths.stream().distinct().limit(MAX_ADDITIONAL_FOCUS_FILES).toList()) {
            GitHubRepositorySnapshot.RepositoryFile file = filesByPath.get(requestedPath);
            if (file == null || !isSourceFile(file) || !includedPaths.add(file.path())) continue;
            enriched.add(new GitHubRepositorySnapshot.RepositoryFile(
                    file.path(), getRawFile(snapshot.reference(), snapshot.defaultBranch(), file.path()), file.size()
            ));
        }
        return new GitHubRepositorySnapshot(
                snapshot.reference(), snapshot.defaultBranch(), snapshot.description(), snapshot.languageBytes(),
                snapshot.allFiles(), List.copyOf(enriched)
        );
    }

    List<String> expandFocusPaths(
            GitHubRepositorySnapshot snapshot,
            List<String> requestedPaths
    ) {
        Map<String, GitHubRepositorySnapshot.RepositoryFile> filesByPath = snapshot.allFiles().stream()
                .collect(java.util.stream.Collectors.toMap(
                        GitHubRepositorySnapshot.RepositoryFile::path, file -> file, (left, right) -> left, LinkedHashMap::new
                ));
        LinkedHashSet<String> expanded = requestedPaths.stream()
                .filter(filesByPath::containsKey)
                .filter(path -> isSourceFile(filesByPath.get(path)))
                .limit(MAX_ADDITIONAL_FOCUS_FILES)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<String> relatedCandidates = expanded.stream()
                .flatMap(path -> snapshot.allFiles().stream()
                        .filter(file -> isSourceFile(file) && sameDirectory(path, file.path()))
                        .map(GitHubRepositorySnapshot.RepositoryFile::path))
                .filter(path -> !expanded.contains(path))
                .distinct()
                .sorted(Comparator.comparingInt(this::score).reversed().thenComparing(path -> path))
                .limit(MAX_RELATED_FOCUS_FILES)
                .toList();
        relatedCandidates.forEach(expanded::add);
        return expanded.stream().limit(MAX_ADDITIONAL_FOCUS_FILES).toList();
    }

    private Map<String, Integer> readLanguages(GitHubRepositoryReference reference) {
        JsonNode response = getJson("/repos/" + reference.owner() + "/" + reference.repository() + "/languages");
        Map<String, Integer> languages = new LinkedHashMap<>();
        response.fields().forEachRemaining(entry -> languages.put(entry.getKey(), entry.getValue().asInt()));
        return languages;
    }

    private List<GitHubRepositorySnapshot.RepositoryFile> readTree(GitHubRepositoryReference reference, String branch) {
        JsonNode response = getJson("/repos/" + reference.owner() + "/" + reference.repository()
                + "/git/trees/" + encodePathPart(branch) + "?recursive=1");
        if (response.path("truncated").asBoolean(false)) {
            throw new ProjectAnalysisException("This repository has too many files for a safe recursive analysis.");
        }

        List<GitHubRepositorySnapshot.RepositoryFile> files = new ArrayList<>();
        for (JsonNode entry : response.path("tree")) {
            if (!"blob".equals(entry.path("type").asText())) continue;
            files.add(new GitHubRepositorySnapshot.RepositoryFile(
                    entry.path("path").asText(), "", entry.path("size").asLong()
            ));
        }
        return files;
    }

    private List<GitHubRepositorySnapshot.RepositoryFile> readSelectedFiles(
            GitHubRepositoryReference reference,
            String branch,
            List<GitHubRepositorySnapshot.RepositoryFile> allFiles
    ) {
        Set<String> selectedPaths = new LinkedHashSet<>();
        allFiles.stream()
                .filter(file -> isPrimaryFile(file.path()))
                .filter(file -> file.size() <= MAX_FILE_BYTES)
                .map(GitHubRepositorySnapshot.RepositoryFile::path)
                .forEach(selectedPaths::add);
        allFiles.stream()
                .filter(this::isSourceFile)
                .sorted(Comparator.comparingInt((GitHubRepositorySnapshot.RepositoryFile file) -> score(file.path())).reversed()
                        .thenComparing(GitHubRepositorySnapshot.RepositoryFile::path))
                .limit(MAX_ANALYZED_SOURCE_FILES)
                .map(GitHubRepositorySnapshot.RepositoryFile::path)
                .forEach(selectedPaths::add);

        return selectedPaths.stream()
                .map(path -> new GitHubRepositorySnapshot.RepositoryFile(
                        path, getRawFile(reference, branch, path), findSize(allFiles, path)
                ))
                .toList();
    }

    private String getRawFile(GitHubRepositoryReference reference, String branch, String path) {
        String encodedPath = java.util.Arrays.stream(path.split("/"))
                .map(this::encodePathPart)
                .reduce((left, right) -> left + "/" + right)
                .orElseThrow();
        return getText(
                "/repos/" + reference.owner() + "/" + reference.repository() + "/contents/" + encodedPath
                        + "?ref=" + encodePathPart(branch),
                "application/vnd.github.raw+json"
        );
    }

    private long findSize(List<GitHubRepositorySnapshot.RepositoryFile> allFiles, String path) {
        return allFiles.stream().filter(file -> file.path().equals(path)).findFirst()
                .map(GitHubRepositorySnapshot.RepositoryFile::size).orElse(0L);
    }

    private boolean sameDirectory(String left, String right) {
        int leftSlash = left.lastIndexOf('/');
        int rightSlash = right.lastIndexOf('/');
        String leftDirectory = leftSlash < 0 ? "" : left.substring(0, leftSlash);
        String rightDirectory = rightSlash < 0 ? "" : right.substring(0, rightSlash);
        return leftDirectory.equals(rightDirectory);
    }

    private boolean isPrimaryFile(String path) {
        if (isIgnored(path)) return false;
        String filename = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return PRIMARY_FILENAMES.contains(filename);
    }

    private boolean isSourceFile(GitHubRepositorySnapshot.RepositoryFile file) {
        return !isIgnored(file.path()) && file.size() > 0 && file.size() <= MAX_FILE_BYTES
                && TEXT_EXTENSIONS.contains(file.extension());
    }

    private boolean isIgnored(String path) {
        String normalized = "/" + path.toLowerCase(Locale.ROOT) + "/";
        return normalized.contains("/node_modules/") || normalized.contains("/.git/") || normalized.contains("/dist/")
                || normalized.contains("/build/") || normalized.contains("/target/") || normalized.contains("/coverage/")
                || normalized.contains("/__tests__/") || normalized.contains("/test/") || normalized.endsWith(".min.js/")
                || normalized.contains("/.env") || normalized.contains("/vendor/");
    }

    private int score(String path) {
        String value = path.toLowerCase(Locale.ROOT);
        int score = 0;
        if (value.contains("BoardController") || value.contains("router") || value.contains("routes/")) score += 8;
        if (value.contains("service") || value.contains("usecase")) score += 7;
        if (value.contains("repository") || value.contains("dao")) score += 6;
        if (value.contains("client") || value.contains("integration") || value.contains("gateway")) score += 6;
        if (value.contains("entity") || value.contains("model") || value.contains("schema")) score += 5;
        if (value.contains("security") || value.contains("auth") || value.contains("jwt")) score += 5;
        if (value.contains("application") || value.contains("main.")) score += 4;
        if (value.contains("page") || value.contains("component")) score += 3;
        return score;
    }

    private JsonNode getJson(String path) {
        try {
            return objectMapper.readTree(getText(path, "application/vnd.github+json"));
        } catch (IOException exception) {
            throw new ProjectAnalysisException("GitHub returned a response that could not be read.");
        }
    }

    private String getText(String path, String accept) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.github.com" + path))
                .timeout(Duration.ofSeconds(18))
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2026-03-10")
                .header("User-Agent", "JobPilot-AI-Project-Analyzer")
                .GET();
        if (!githubToken.isBlank()) builder.header("Authorization", "Bearer " + githubToken);
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) return response.body();
            if (response.statusCode() == 404) {
                throw new ProjectAnalysisException("The repository was not found or this server does not have access to it.");
            }
            if (response.statusCode() == 403 || response.statusCode() == 429) {
                throw new ProjectAnalysisException("GitHub API rate limit reached. Configure GITHUB_TOKEN and try again.");
            }
            throw new ProjectAnalysisException("Could not read the GitHub repository (HTTP " + response.statusCode() + ").");
        } catch (IOException exception) {
            throw new ProjectAnalysisException("Could not connect to the GitHub API.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProjectAnalysisException("GitHub analysis request was interrupted.");
        }
    }

    private String encodePathPart(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

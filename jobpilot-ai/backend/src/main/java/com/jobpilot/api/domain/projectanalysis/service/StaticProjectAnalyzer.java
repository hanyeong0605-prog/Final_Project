package com.jobpilot.api.domain.projectanalysis.service;

import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class StaticProjectAnalyzer {
    private static final Pattern JAVA_SYMBOL = Pattern.compile("(?:class|record|interface)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern JS_SYMBOL = Pattern.compile("(?:function|const|class)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern SPRING_MAPPING = Pattern.compile("@(Get|Post|Put|Delete|Patch|Request)Mapping");
    private static final Map<String, String> DOMAIN_LABELS = Map.ofEntries(
            Map.entry("matching", "Recruitment matching"),
            Map.entry("jobposting", "Job posting management"),
            Map.entry("jobs", "Job recommendation"),
            Map.entry("opportunities", "Opportunity recommendation"),
            Map.entry("planner", "Schedule planning"),
            Map.entry("profile", "Profile management"),
            Map.entry("interests", "Interest management")
    );

    GitHubProjectAnalysisResponse analyze(GitHubRepositorySnapshot snapshot) {
        List<GitHubProjectAnalysisResponse.TechnologyFact> technologies = technologies(snapshot);
        List<GitHubProjectAnalysisResponse.CoreFile> coreFiles = coreFiles(snapshot.analyzedFiles());
        List<GitHubProjectAnalysisResponse.FeatureCandidate> candidates = featureCandidates(coreFiles);
        List<GitHubProjectAnalysisResponse.ArchitectureLayer> architecture = architecture(coreFiles, snapshot.analyzedFiles());
        Map<String, Integer> fileTypes = fileTypes(snapshot.allFiles());
        List<String> notices = List.of(
                "The analysis is based on the current " + snapshot.defaultBranch() + " branch.",
                "Generated assets, test folders, environment files, and minified files are excluded.",
                "Feature candidates are evidence-based suggestions and should be selected by the project owner before use in a presentation."
        );

        return new GitHubProjectAnalysisResponse(
                new GitHubProjectAnalysisResponse.RepositoryInfo(
                        snapshot.reference().owner(), snapshot.reference().repository(),
                        snapshot.reference().owner() + "/" + snapshot.reference().repository(), snapshot.reference().htmlUrl(),
                        snapshot.defaultBranch(), blankToNull(snapshot.description()), Instant.now()
                ),
                overview(snapshot, technologies),
                "Static analysis has extracted an initial presentation brief from the repository structure and evidence files.",
                "STATIC",
                technologies,
                architecture,
                candidates,
                coreFiles,
                new GitHubProjectAnalysisResponse.AnalysisMetrics(
                        snapshot.allFiles().size(), countSourceFiles(snapshot.allFiles()), snapshot.analyzedFiles().size(), fileTypes
                ),
                notices
        );
    }

    GitHubProjectAnalysisResponse applyAiSummary(
            GitHubProjectAnalysisResponse analysis,
            GeminiProjectSummaryClient.AiSummary aiSummary
    ) {
        Map<String, String> summaries = aiSummary.featureSummaries();
        List<GitHubProjectAnalysisResponse.FeatureCandidate> candidates = analysis.featureCandidates().stream()
                .map(candidate -> new GitHubProjectAnalysisResponse.FeatureCandidate(
                        candidate.id(), candidate.title(), summaries.getOrDefault(candidate.id(), candidate.description()),
                        candidate.confidence(), candidate.evidence(), candidate.score()
                )).toList();
        return new GitHubProjectAnalysisResponse(
                analysis.repository(),
                blankToNull(aiSummary.overview()) == null ? analysis.overview() : aiSummary.overview(),
                blankToNull(aiSummary.narrative()) == null ? analysis.aiNarrative() : aiSummary.narrative(),
                "GEMINI",
                analysis.technologyStack(), analysis.architecture(), candidates, analysis.coreFiles(), analysis.metrics(), analysis.notices()
        );
    }

    private List<GitHubProjectAnalysisResponse.TechnologyFact> technologies(GitHubRepositorySnapshot snapshot) {
        Map<String, TechnologyEvidence> facts = new LinkedHashMap<>();
        String allContent = snapshot.analyzedFiles().stream()
                .map(GitHubRepositorySnapshot.RepositoryFile::content)
                .reduce("", (left, right) -> left + "\n" + right)
                .toLowerCase(Locale.ROOT);
        List<String> paths = snapshot.analyzedFiles().stream().map(GitHubRepositorySnapshot.RepositoryFile::path).toList();
        if (allContent.contains("spring-boot")) addFact(facts, "Spring Boot", "backend", pathsContaining(paths, "pom.xml"));
        if (allContent.contains("spring-boot-starter-data-jpa") || allContent.contains("jparepository")) addFact(facts, "Spring Data JPA", "backend", pathsContaining(paths, "pom.xml", "repository"));
        if (allContent.contains("mysql")) addFact(facts, "MySQL", "database", pathsContaining(paths, "pom.xml", "application.yml", "application.yaml"));
        if (allContent.contains("flyway")) addFact(facts, "Flyway", "database", pathsContaining(paths, "pom.xml"));
        if (allContent.contains("\"react\"")) addFact(facts, "React", "frontend", pathsContaining(paths, "package.json"));
        if (allContent.contains("react-router")) addFact(facts, "React Router", "frontend", pathsContaining(paths, "package.json", "router"));
        if (allContent.contains("vite")) addFact(facts, "Vite", "frontend", pathsContaining(paths, "package.json", "vite.config"));
        snapshot.languageBytes().keySet().forEach(language -> addFact(facts, language, "language", List.of("GitHub language statistics")));
        return facts.entrySet().stream()
                .map(entry -> new GitHubProjectAnalysisResponse.TechnologyFact(
                        entry.getKey(), entry.getValue().category(), List.copyOf(entry.getValue().evidence())
                ))
                .toList();
    }

    private List<GitHubProjectAnalysisResponse.CoreFile> coreFiles(List<GitHubRepositorySnapshot.RepositoryFile> files) {
        return files.stream()
                .filter(file -> isCodeFile(file.path()))
                .map(file -> new GitHubProjectAnalysisResponse.CoreFile(
                        file.path(), role(file.path(), file.content()), symbols(file), excerpt(file.content()), score(file.path(), file.content())
                ))
                .sorted(Comparator.comparingInt(GitHubProjectAnalysisResponse.CoreFile::score).reversed()
                        .thenComparing(GitHubProjectAnalysisResponse.CoreFile::path))
                .limit(8)
                .toList();
    }

    private List<GitHubProjectAnalysisResponse.FeatureCandidate> featureCandidates(List<GitHubProjectAnalysisResponse.CoreFile> coreFiles) {
        Map<String, List<GitHubProjectAnalysisResponse.CoreFile>> groups = new LinkedHashMap<>();
        for (GitHubProjectAnalysisResponse.CoreFile coreFile : coreFiles) {
            groups.computeIfAbsent(domainKey(coreFile.path()), ignored -> new ArrayList<>()).add(coreFile);
        }
        return groups.entrySet().stream()
                .map(entry -> {
                    List<GitHubProjectAnalysisResponse.CoreFile> files = entry.getValue();
                    int score = files.stream().mapToInt(GitHubProjectAnalysisResponse.CoreFile::score).sum();
                    String title = DOMAIN_LABELS.getOrDefault(entry.getKey(), displayFallbackTitle(files.get(0).path()));
                    String roles = files.stream().map(GitHubProjectAnalysisResponse.CoreFile::role).distinct().reduce((left, right) -> left + ", " + right).orElse("application");
                    return new GitHubProjectAnalysisResponse.FeatureCandidate(
                            entry.getKey(), title, roles + " evidence indicates an independently explainable project capability.",
                            files.size() >= 2 ? "HIGH" : "MEDIUM",
                            files.stream().map(GitHubProjectAnalysisResponse.CoreFile::path).toList(), score
                    );
                })
                .sorted(Comparator.comparingInt(GitHubProjectAnalysisResponse.FeatureCandidate::score).reversed())
                .limit(5)
                .toList();
    }

    private List<GitHubProjectAnalysisResponse.ArchitectureLayer> architecture(
            List<GitHubProjectAnalysisResponse.CoreFile> coreFiles,
            List<GitHubRepositorySnapshot.RepositoryFile> analyzedFiles
    ) {
        List<GitHubProjectAnalysisResponse.ArchitectureLayer> layers = new ArrayList<>();
        List<String> frontend = pathsByPrefix(analyzedFiles, "frontend/");
        if (!frontend.isEmpty()) layers.add(new GitHubProjectAnalysisResponse.ArchitectureLayer("Frontend", "Pages and feature modules form the user-facing application layer.", frontend));
        List<String> controllers = pathsByRole(coreFiles, "API Controller");
        if (!controllers.isEmpty()) layers.add(new GitHubProjectAnalysisResponse.ArchitectureLayer("API", "Controllers or routers receive HTTP requests.", controllers));
        List<String> services = pathsByRole(coreFiles, "Business Service");
        if (!services.isEmpty()) layers.add(new GitHubProjectAnalysisResponse.ArchitectureLayer("Business logic", "Services hold domain behavior and orchestration.", services));
        List<String> persistence = coreFiles.stream()
                .filter(file -> file.role().equals("Data Access") || file.role().equals("Domain Model"))
                .map(GitHubProjectAnalysisResponse.CoreFile::path).toList();
        if (!persistence.isEmpty()) layers.add(new GitHubProjectAnalysisResponse.ArchitectureLayer("Data", "Repositories and domain models separate persistence concerns.", persistence));
        return layers;
    }

    private String overview(GitHubRepositorySnapshot snapshot, List<GitHubProjectAnalysisResponse.TechnologyFact> technologies) {
        if (blankToNull(snapshot.description()) != null) return snapshot.description();
        String stack = technologies.stream()
                .filter(technology -> !technology.category().equals("language"))
                .map(GitHubProjectAnalysisResponse.TechnologyFact::name)
                .limit(3)
                .reduce((left, right) -> left + ", " + right).orElse("source code");
        return snapshot.reference().repository() + " is a project built around " + stack + ". The preview identifies implementation evidence before a presentation outline is generated.";
    }

    private void addFact(Map<String, TechnologyEvidence> facts, String name, String category, List<String> evidence) {
        if (evidence.isEmpty()) return;
        facts.computeIfAbsent(name, ignored -> new TechnologyEvidence(category, new ArrayList<>())).evidence().addAll(evidence);
    }

    private List<String> pathsContaining(List<String> paths, String... fragments) {
        return paths.stream()
                .filter(path -> java.util.Arrays.stream(fragments)
                        .anyMatch(fragment -> path.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT))))
                .toList();
    }

    private Map<String, Integer> fileTypes(List<GitHubRepositorySnapshot.RepositoryFile> files) {
        Map<String, Integer> types = new LinkedHashMap<>();
        for (GitHubRepositorySnapshot.RepositoryFile file : files) {
            String extension = file.extension();
            if (!extension.isBlank()) types.merge(extension, 1, Integer::sum);
        }
        return types.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(6)
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    private int countSourceFiles(List<GitHubRepositorySnapshot.RepositoryFile> files) {
        return (int) files.stream().filter(file -> isCodeFile(file.path())).count();
    }

    private boolean isCodeFile(String path) {
        return path.matches(".*\\.(java|kt|ts|tsx|js|jsx|py|go|rb|cs|php)$");
    }

    private String role(String path, String content) {
        String value = (path + "\n" + content).toLowerCase(Locale.ROOT);
        if (value.contains("@restcontroller") || value.contains("@controller") || value.contains("@getmapping") || value.contains("@postmapping") || path.toLowerCase(Locale.ROOT).contains("router")) return "API Controller";
        if (value.contains("@service") || path.toLowerCase(Locale.ROOT).contains("service")) return "Business Service";
        if (value.contains("jparepository") || path.toLowerCase(Locale.ROOT).contains("repository")) return "Data Access";
        if (value.contains("@entity") || path.toLowerCase(Locale.ROOT).contains("entity")) return "Domain Model";
        if (path.toLowerCase(Locale.ROOT).contains("page")) return "Screen";
        if (path.toLowerCase(Locale.ROOT).contains("component")) return "UI Component";
        return "Application Code";
    }

    private int score(String path, String content) {
        String value = (path + "\n" + content).toLowerCase(Locale.ROOT);
        int score = 0;
        if (value.contains("@restcontroller") || value.contains("@getmapping") || value.contains("@postmapping")) score += 9;
        if (value.contains("@service")) score += 8;
        if (value.contains("jparepository")) score += 7;
        if (value.contains("@entity")) score += 6;
        if (value.contains("createbrowserrouter") || value.contains("route")) score += 5;
        if (path.toLowerCase(Locale.ROOT).contains("page")) score += 4;
        if (path.toLowerCase(Locale.ROOT).contains("application")) score += 3;
        return score;
    }

    private List<String> symbols(GitHubRepositorySnapshot.RepositoryFile file) {
        Pattern pattern = file.extension().equals("java") ? JAVA_SYMBOL : JS_SYMBOL;
        List<String> symbols = new ArrayList<>();
        Matcher matcher = pattern.matcher(file.content());
        while (matcher.find() && symbols.size() < 4) symbols.add(matcher.group(1));
        Matcher mappingMatcher = SPRING_MAPPING.matcher(file.content());
        while (mappingMatcher.find() && symbols.size() < 5) symbols.add("@" + mappingMatcher.group(1) + "Mapping");
        return symbols.isEmpty() ? List.of(file.filename()) : symbols;
    }

    private String excerpt(String content) {
        return content.lines().map(String::trim).filter(line -> !line.isBlank()).limit(7)
                .reduce((left, right) -> left + "\n" + right)
                .map(value -> value.length() > 420 ? value.substring(0, 420) + "..." : value)
                .orElse("");
    }

    private String domainKey(String path) {
        String[] segments = path.toLowerCase(Locale.ROOT).split("/");
        for (int index = 0; index < segments.length - 1; index++) {
            if ((segments[index].equals("domain") || segments[index].equals("features")) && index + 1 < segments.length - 1) return segments[index + 1];
        }
        String filename = path.substring(path.lastIndexOf('/') + 1);
        return filename.replaceFirst("\\.[^.]+$", "").replaceAll("(Controller|Service|Repository|Entity|Page|Component)$", "").toLowerCase(Locale.ROOT);
    }

    private String displayFallbackTitle(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.[^.]+$", "");
        return filename + " implementation";
    }

    private List<String> pathsByPrefix(List<GitHubRepositorySnapshot.RepositoryFile> files, String prefix) {
        return files.stream().map(GitHubRepositorySnapshot.RepositoryFile::path).filter(path -> path.startsWith(prefix)).limit(3).toList();
    }

    private List<String> pathsByRole(List<GitHubProjectAnalysisResponse.CoreFile> files, String role) {
        return files.stream().filter(file -> file.role().equals(role)).map(GitHubProjectAnalysisResponse.CoreFile::path).toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record TechnologyEvidence(String category, List<String> evidence) {
    }
}

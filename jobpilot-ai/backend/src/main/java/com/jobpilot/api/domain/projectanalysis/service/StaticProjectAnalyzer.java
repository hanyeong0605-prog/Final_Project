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
    // SPRING_MAPPING은 symbols() 표시용으로 @RequestMapping(클래스 레벨 base path)까지
    // 포함해서 잡는 게 맞지만, allMappedMethodExcerpts()에서 그대로 쓰면 클래스 레벨
    // @RequestMapping을 "메서드"로 착각해서 클래스 여는 중괄호부터 통째로 캡처해버리는
    // 버그가 났다(2026-08-14, 여러 메서드가 한 발췌에 섞여 나옴) - 실제 HTTP 메서드별
    // 매핑 어노테이션만 골라 메서드 단위로 정확히 찾도록 별도 패턴을 둔다.
    private static final Pattern METHOD_MAPPING = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping");
    private static final Pattern JAVA_EXTENDS = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)[^\\{]*\\bextends\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern JAVA_IMPLEMENTS = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)[^\\{]*\\bimplements\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern JAVA_NEW = Pattern.compile("\\bnew\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Map<String, String> DOMAIN_LABELS = Map.ofEntries(
            Map.entry("matching", "채용 매칭"),
            Map.entry("jobposting", "채용 공고 관리"),
            Map.entry("jobs", "맞춤 채용 공고"),
            Map.entry("opportunities", "성장 기회 추천"),
            Map.entry("planner", "일정 관리"),
            Map.entry("profile", "역량 프로필 관리"),
            Map.entry("interests", "관심 항목 관리")
    );

    GitHubProjectAnalysisResponse analyze(GitHubRepositorySnapshot snapshot) {
        return analyze(snapshot, List.of());
    }

    GitHubProjectAnalysisResponse analyze(GitHubRepositorySnapshot snapshot, List<String> focusPaths) {
        List<GitHubProjectAnalysisResponse.TechnologyFact> technologies = technologies(snapshot);
        List<GitHubProjectAnalysisResponse.CoreFile> coreFiles = coreFiles(snapshot.analyzedFiles(), java.util.Set.copyOf(focusPaths));
        List<GitHubProjectAnalysisResponse.IntegrationFact> integrations = integrations(snapshot);
        List<GitHubProjectAnalysisResponse.FeatureCandidate> candidates = featureCandidates(coreFiles);
        List<GitHubProjectAnalysisResponse.ImplementationStory> implementations = implementationStories(candidates, coreFiles);
        List<GitHubProjectAnalysisResponse.ArchitectureLayer> architecture = architecture(coreFiles, snapshot.analyzedFiles());
        GitHubProjectAnalysisResponse.ProjectProfile profile = projectProfile(snapshot, coreFiles, integrations);
        Map<String, Integer> fileTypes = fileTypes(snapshot.allFiles());
        List<String> notices = List.of(
                "현재 " + snapshot.defaultBranch() + " 브랜치의 코드 기준으로 분석했습니다.",
                "생성 파일, 테스트 폴더, 환경 파일, 축소된 파일은 분석에서 제외했습니다.",
                "외부 API·인증·DB·서비스 역할은 실제 코드 신호가 발견된 경우에만 핵심 근거로 표시합니다."
        );

        return new GitHubProjectAnalysisResponse(
                new GitHubProjectAnalysisResponse.RepositoryInfo(
                        snapshot.reference().owner(), snapshot.reference().repository(),
                        snapshot.reference().owner() + "/" + snapshot.reference().repository(), snapshot.reference().htmlUrl(),
                        snapshot.defaultBranch(), blankToNull(snapshot.description()), Instant.now()
                ),
                profile.summary(),
                "정적 분석이 프로젝트 성격, 파일 역할, 선정 이유를 코드 근거 중심으로 정리했습니다. 발표용 핵심 기능은 중요도가 높은 근거부터 선택하는 것이 좋습니다.",
                "STATIC",
                profile,
                technologies,
                architecture,
                integrations,
                List.of(),
                implementations,
                candidates,
                coreFiles,
                new GitHubProjectAnalysisResponse.AnalysisMetrics(
                        snapshot.allFiles().size(), countSourceFiles(snapshot.allFiles()), snapshot.analyzedFiles().size(), fileTypes
                ),
                notices,
                null
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
        GitHubProjectAnalysisResponse.ProjectProfile staticProfile = analysis.projectProfile();
        GitHubProjectAnalysisResponse.ProjectProfile profile = new GitHubProjectAnalysisResponse.ProjectProfile(
                valueOrFallback(aiSummary.projectClassification(), staticProfile.classification()),
                valueOrFallback(aiSummary.projectSummary(), staticProfile.summary()),
                staticProfile.confidence(), staticProfile.evidence(), staticProfile.limitations()
        );
        return new GitHubProjectAnalysisResponse(
                analysis.repository(),
                valueOrFallback(aiSummary.overview(), analysis.overview()),
                valueOrFallback(aiSummary.narrative(), analysis.aiNarrative()),
                "GEMINI",
                profile,
                analysis.technologyStack(),
                analysis.architecture(),
                analysis.integrations(),
                aiCodeFlows(aiSummary.flows(), analysis.coreFiles()),
                aiImplementationStories(aiSummary.implementations(), analysis.coreFiles(), analysis.technologyStack(), analysis.implementations()),
                candidates,
                aiCoreFiles(analysis.coreFiles(), aiSummary.fileExplanations()),
                analysis.metrics(),
                analysis.notices(),
                null
        );
    }

    GitHubProjectAnalysisResponse markGeminiFallback(GitHubProjectAnalysisResponse analysis, String generatedOutput) {
        List<String> notices = new ArrayList<>(analysis.notices());
        notices.add("Gemini 호출은 완료되었지만 구조화된 코드 설명을 적용하지 못해 정적 코드 근거를 표시합니다.");
        return new GitHubProjectAnalysisResponse(
                analysis.repository(), analysis.overview(), analysis.aiNarrative(), "GEMINI_FALLBACK",
                analysis.projectProfile(), analysis.technologyStack(), analysis.architecture(), analysis.integrations(),
                analysis.codeFlows(), analysis.implementations(), analysis.featureCandidates(), analysis.coreFiles(),
                analysis.metrics(), notices, generatedOutput
        );
    }

    private List<GitHubProjectAnalysisResponse.CoreFile> aiCoreFiles(
            List<GitHubProjectAnalysisResponse.CoreFile> files,
            Map<String, GeminiProjectSummaryClient.FileExplanation> explanations
    ) {
        return files.stream().map(file -> {
            GeminiProjectSummaryClient.FileExplanation explanation = explanations.get(file.path());
            if (explanation == null) return file;
            return new GitHubProjectAnalysisResponse.CoreFile(
                    file.path(), file.role(), valueOrFallback(explanation.responsibility(), file.responsibility()),
                    file.symbols(), file.excerpt(), file.score(), normalizedImportance(explanation.importance(), file.importance()),
                    valueOrFallback(explanation.selectionReason(), file.selectionReason()), file.methodExcerpts()
            );
        }).toList();
    }

    private List<GitHubProjectAnalysisResponse.CodeFlow> aiCodeFlows(
            List<GeminiProjectSummaryClient.AiCodeFlow> flows,
            List<GitHubProjectAnalysisResponse.CoreFile> coreFiles
    ) {
        java.util.Set<String> allowedPaths = coreFiles.stream()
                .map(GitHubProjectAnalysisResponse.CoreFile::path)
                .collect(java.util.stream.Collectors.toSet());
        return flows.stream()
                .limit(4)
                .map(flow -> new GitHubProjectAnalysisResponse.CodeFlow(
                        flow.title(), flow.description(),
                        flow.evidence().stream().filter(allowedPaths::contains).distinct().toList(), flow.confidence()
                ))
                .filter(flow -> !blank(flow.title()) && !blank(flow.description()) && !flow.evidence().isEmpty())
                .toList();
    }

    private List<GitHubProjectAnalysisResponse.ImplementationStory> implementationStories(
            List<GitHubProjectAnalysisResponse.FeatureCandidate> candidates,
            List<GitHubProjectAnalysisResponse.CoreFile> coreFiles
    ) {
        return candidates.stream().map(candidate -> {
            List<GitHubProjectAnalysisResponse.CoreFile> files = candidate.evidence().stream()
                    .map(path -> coreFiles.stream().filter(file -> file.path().equals(path)).findFirst().orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            // "구현 방식"이 컨트롤러마다 똑같은 문장("관련 코드의 역할과 연결 구조를...")만
            // 나온다는 지적(2026-08-14) - AI 요약이 없는 정적 모드에서도 실제 추출된
            // HTTP 매핑 신호(endpointSignals)가 있으면 그걸로 이 기능이 실제 무엇을
            // 하는지 설명하고, 신호가 없는(컨트롤러가 아닌) 그룹만 기존 일반 문장을 쓴다.
            List<EndpointSignal> signals = endpointSignals(files);
            String mechanism = signals.isEmpty()
                    ? "관련 코드의 역할과 연결 구조를 정적 신호로 확인했습니다."
                    : mechanismNarrative(signals);
            return new GitHubProjectAnalysisResponse.ImplementationStory(
                    candidate.id(), candidate.title(), candidate.description(),
                    mechanism, List.of(),
                    files.stream()
                            .map(file -> new GitHubProjectAnalysisResponse.CodeEvidence(
                                    file.path(), representativeSymbol(file), file.responsibility()
                            )).toList()
            );
        }).toList();
    }

    // 정적(비-AI) 모드에서 CoreFile.excerpt/첫 번째 methodExcerpts 항목이 이 파일의 "대표"
    // 발췌인데, evidence.symbol은 지금까지 file.symbols().getFirst()(클래스 이름)를 썼다.
    // 프론트/PDF/PPTX 렌더러는 evidence.symbol과 methodExcerpts[].symbol이 정확히 일치할
    // 때만 그 메서드 발췌를 골라 쓰므로, 클래스 이름을 넘기면 항상 매칭에 실패해 "파일의
    // 첫 매핑 메서드"로 조용히 폴백한다(우연히 대부분 맞아 보였을 뿐). 대표 발췌와 같은
    // 메서드의 실제 심볼을 넘겨 일관성을 맞춘다.
    private String representativeSymbol(GitHubProjectAnalysisResponse.CoreFile file) {
        return file.methodExcerpts().isEmpty() ? file.symbols().getFirst() : file.methodExcerpts().get(0).symbol();
    }

    private List<GitHubProjectAnalysisResponse.ImplementationStory> aiImplementationStories(
            List<GeminiProjectSummaryClient.AiImplementation> implementations,
            List<GitHubProjectAnalysisResponse.CoreFile> coreFiles,
            List<GitHubProjectAnalysisResponse.TechnologyFact> technologies,
            List<GitHubProjectAnalysisResponse.ImplementationStory> fallback
    ) {
        Map<String, GitHubProjectAnalysisResponse.CoreFile> filesByPath = coreFiles.stream()
                .collect(java.util.stream.Collectors.toMap(
                        GitHubProjectAnalysisResponse.CoreFile::path, file -> file, (left, right) -> left, LinkedHashMap::new
                ));
        java.util.Set<String> knownTechnologies = technologies.stream()
                .map(GitHubProjectAnalysisResponse.TechnologyFact::name)
                .collect(java.util.stream.Collectors.toSet());
        List<GitHubProjectAnalysisResponse.ImplementationStory> result = new ArrayList<>();
        for (GeminiProjectSummaryClient.AiImplementation implementation : implementations) {
            List<GitHubProjectAnalysisResponse.CodeEvidence> evidence = implementation.evidence().stream()
                    .map(item -> aiCodeEvidence(item, filesByPath))
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            if (blank(implementation.title()) || blank(implementation.description()) || evidence.isEmpty()) continue;
            List<String> supportedTechnologies = implementation.technologies().stream()
                    .filter(knownTechnologies::contains).distinct().limit(5).toList();
            result.add(new GitHubProjectAnalysisResponse.ImplementationStory(
                    "implementation-" + (result.size() + 1), valueOrFallback(implementation.title(), "구현 코드"),
                    valueOrFallback(implementation.description(), ""),
                    valueOrFallback(implementation.mechanism(), "관련 코드에서 확인된 처리 구조입니다."),
                    supportedTechnologies, evidence
            ));
            if (result.size() == 5) break;
        }
        return result.isEmpty() ? fallback : result;
    }

    private GitHubProjectAnalysisResponse.CodeEvidence aiCodeEvidence(
            GeminiProjectSummaryClient.AiCodeEvidence evidence,
            Map<String, GitHubProjectAnalysisResponse.CoreFile> filesByPath
    ) {
        GitHubProjectAnalysisResponse.CoreFile file = filesByPath.get(evidence.path());
        if (file == null) return null;
        String symbol = evidence.symbol();
        // methodExcerpts에 있는 실제 메서드 이름이면 Gemini가 지목한 심볼을 그대로 신뢰한다
        // (2026-08-14 이전엔 file.symbols()/file.excerpt()에 없으면 무조건 클래스 이름으로
        // 덮어써버려서, 파일에 매핑 메서드가 여러 개일 때 Gemini가 정확히 지목한 메서드
        // 이름이 거의 항상 버려지는 문제가 있었다).
        // requestedSymbol: symbol은 아래에서 재할당되므로 그 값을 그대로 람다에 캡처할 수
        // 없다("local variables referenced from a lambda expression must be final or
        // effectively final" 컴파일 에러, 2026-08-14 실제로 겪음) - 재할당 전 값을 별도의
        // 불변 변수에 복사해 람다에 넘긴다.
        String requestedSymbol = symbol;
        boolean isKnownMethodSymbol = requestedSymbol != null
                && file.methodExcerpts().stream().anyMatch(candidate -> candidate.symbol().equals(requestedSymbol));
        if (blank(symbol) || (!isKnownMethodSymbol && !file.symbols().contains(symbol) && !file.excerpt().contains(symbol))) {
            symbol = file.symbols().getFirst();
        }
        return new GitHubProjectAnalysisResponse.CodeEvidence(
                file.path(), symbol, valueOrFallback(evidence.description(), file.responsibility())
        );
    }

    private String normalizedImportance(String value, String fallback) {
        return switch (value == null ? "" : value.trim().toUpperCase(Locale.ROOT)) {
            case "CORE", "STRUCTURAL", "REFERENCE" -> value.trim().toUpperCase(Locale.ROOT);
            default -> fallback;
        };
    }

    private String valueOrFallback(String value, String fallback) {
        if (blank(value)) return fallback;
        String normalized = value.trim();
        return normalized.length() > 700 ? normalized.substring(0, 700) : normalized;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private List<GitHubProjectAnalysisResponse.TechnologyFact> technologies(GitHubRepositorySnapshot snapshot) {
        Map<String, TechnologyEvidence> facts = new LinkedHashMap<>();
        String allContent = allContent(snapshot);
        List<String> paths = snapshot.analyzedFiles().stream().map(GitHubRepositorySnapshot.RepositoryFile::path).toList();
        if (allContent.contains("spring-boot")) addFact(facts, "Spring Boot", "backend", pathsContaining(paths, "pom.xml"));
        if (allContent.contains("spring-boot-starter-data-jpa") || allContent.contains("jparepository")) addFact(facts, "Spring Data JPA", "backend", pathsContaining(paths, "pom.xml", "repository"));
        if (allContent.contains("mysql")) addFact(facts, "MySQL", "database", pathsContaining(paths, "pom.xml", "application.yml", "application.yaml"));
        if (allContent.contains("flyway")) addFact(facts, "Flyway", "database", pathsContaining(paths, "pom.xml"));
        if (allContent.contains("\"react\"")) addFact(facts, "React", "frontend", pathsContaining(paths, "package.json"));
        if (allContent.contains("react-router")) addFact(facts, "React Router", "frontend", pathsContaining(paths, "package.json", "router"));
        if (allContent.contains("vite")) addFact(facts, "Vite", "frontend", pathsContaining(paths, "package.json", "vite.config"));
        snapshot.languageBytes().keySet().forEach(language -> addFact(facts, language, "language", List.of("GitHub 언어 통계")));
        return facts.entrySet().stream()
                .map(entry -> new GitHubProjectAnalysisResponse.TechnologyFact(
                        entry.getKey(), entry.getValue().category(), List.copyOf(entry.getValue().evidence())
                ))
                .toList();
    }

    private List<GitHubProjectAnalysisResponse.CoreFile> coreFiles(
            List<GitHubRepositorySnapshot.RepositoryFile> files,
            java.util.Set<String> focusPaths
    ) {
        return files.stream()
                .filter(file -> isCodeFile(file.path()))
                .map(file -> {
                    String role = role(file.path(), file.content());
                    int score = score(file.path(), file.content()) + (focusPaths.contains(file.path()) ? 12 : 0);
                    List<GitHubProjectAnalysisResponse.MethodExcerpt> methodExcerpts = allMappedMethodExcerpts(file.content());
                    return new GitHubProjectAnalysisResponse.CoreFile(
                            file.path(), role, staticResponsibility(role, file), symbols(file),
                            excerptOf(methodExcerpts, file.content()), score,
                            importance(score), selectionReason(role, score, file), methodExcerpts
                    );
                })
                .sorted(Comparator.comparingInt(GitHubProjectAnalysisResponse.CoreFile::score).reversed()
                        .thenComparing(GitHubProjectAnalysisResponse.CoreFile::path))
                .limit(10)
                .toList();
    }

    private List<GitHubProjectAnalysisResponse.IntegrationFact> integrations(GitHubRepositorySnapshot snapshot) {
        String allContent = allContent(snapshot);
        List<GitHubProjectAnalysisResponse.IntegrationFact> facts = new ArrayList<>();
        addIntegration(facts, snapshot, allContent, "resttemplate", "WebClient", "FeignClient", "HTTP 클라이언트", "OUTBOUND",
                "외부 HTTP API를 호출할 수 있는 클라이언트 코드가 확인되었습니다.");
        addIntegration(facts, snapshot, allContent, "axios", "fetch(", "ky(", "프론트엔드 HTTP 호출", "OUTBOUND",
                "프론트엔드에서 외부 또는 백엔드 API를 호출하는 코드가 확인되었습니다.");
        addIntegration(facts, snapshot, allContent, "openai", "generativelanguage.googleapis.com", "@google/genai", "AI API 연동", "OUTBOUND",
                "AI 서비스 SDK 또는 API 주소를 사용하는 코드가 확인되었습니다.");
        addIntegration(facts, snapshot, allContent, "spring-security", "jjwt", "@preauthorize", "인증·권한 처리", "SECURITY",
                "인증 또는 권한 처리와 관련된 코드·설정이 확인되었습니다.");
        addIntegration(facts, snapshot, allContent, "@scheduled", "cron", "schedulingconfig", "스케줄 작업", "BACKGROUND",
                "정해진 시간에 실행되는 배치 또는 스케줄 작업이 확인되었습니다.");
        addIntegration(facts, snapshot, allContent, "kafka", "rabbitmq", "spring-kafka", "메시지 큐", "EVENT",
                "비동기 메시지 처리 인프라와 관련된 코드·설정이 확인되었습니다.");
        return facts;
    }

    private void addIntegration(
            List<GitHubProjectAnalysisResponse.IntegrationFact> facts,
            GitHubRepositorySnapshot snapshot,
            String allContent,
            String firstSignal,
            String secondSignal,
            String thirdSignal,
            String name,
            String direction,
            String description
    ) {
        if (!allContent.contains(firstSignal.toLowerCase(Locale.ROOT))
                && !allContent.contains(secondSignal.toLowerCase(Locale.ROOT))
                && !allContent.contains(thirdSignal.toLowerCase(Locale.ROOT))) return;
        List<String> evidence = snapshot.analyzedFiles().stream()
                .filter(file -> containsAny(file.content(), firstSignal, secondSignal, thirdSignal))
                .map(GitHubRepositorySnapshot.RepositoryFile::path)
                .toList();
        facts.add(new GitHubProjectAnalysisResponse.IntegrationFact(
                name, name, direction, description, evidence
        ));
    }

    private List<GitHubProjectAnalysisResponse.FeatureCandidate> featureCandidates(List<GitHubProjectAnalysisResponse.CoreFile> coreFiles) {
        List<GitHubProjectAnalysisResponse.CoreFile> coreCandidates = coreFiles.stream()
                .filter(file -> file.importance().equals("CORE"))
                .toList();
        if (coreCandidates.isEmpty()) {
            return List.of(new GitHubProjectAnalysisResponse.FeatureCandidate(
                    "structure-review",
                    "프로젝트 구조 확인",
                    "API, 서비스, 데이터 접근처럼 발표 핵심 기능으로 확정할 구조적 신호가 충분하지 않습니다. 현재는 소스 파일 구성과 실행 진입점을 참고용으로 제시합니다.",
                    "LOW",
                    coreFiles.stream().map(GitHubProjectAnalysisResponse.CoreFile::path).toList(),
                    0
            ));
        }

        Map<String, List<GitHubProjectAnalysisResponse.CoreFile>> groups = new LinkedHashMap<>();
        for (GitHubProjectAnalysisResponse.CoreFile coreFile : coreCandidates) {
            groups.computeIfAbsent(domainKey(coreFile.path()), ignored -> new ArrayList<>()).add(coreFile);
        }
        return groups.entrySet().stream()
                .map(entry -> {
                    List<GitHubProjectAnalysisResponse.CoreFile> files = entry.getValue();
                    int score = files.stream().mapToInt(GitHubProjectAnalysisResponse.CoreFile::score).sum();
                    String title = DOMAIN_LABELS.getOrDefault(entry.getKey(), displayFallbackTitle(files.get(0).path()));
                    // 카드 상단 설명이 컨트롤러 역할("API 진입점이라 선정했습니다")만 말하고
                    // 실제 무슨 기능인지는 말을 안 해서, 도메인이 달라도(회원/장소/문화행사 등)
                    // 문장이 전부 똑같아 보인다는 지적(2026-08-14). 실제로 추출된 HTTP
                    // 매핑(경로+메서드명) 신호가 있으면 그걸로 이 그룹이 진짜 어떤 엔드포인트를
                    // 구현하는지 보여주고, 신호가 없는(컨트롤러가 아닌) 그룹만 기존처럼
                    // 파일 selectionReason을 모아 보여준다.
                    List<EndpointSignal> signals = endpointSignals(files);
                    String reasons = signals.isEmpty()
                            ? files.stream().map(GitHubProjectAnalysisResponse.CoreFile::selectionReason)
                                    .distinct().reduce((left, right) -> left + " " + right).orElse("구조적 코드 근거가 확인되었습니다.")
                            : endpointNarrative(signals);
                    return new GitHubProjectAnalysisResponse.FeatureCandidate(
                            entry.getKey(), title, reasons,
                            score >= 16 ? "HIGH" : "MEDIUM",
                            files.stream().map(GitHubProjectAnalysisResponse.CoreFile::path).toList(), score
                    );
                })
                .sorted(Comparator.comparingInt(GitHubProjectAnalysisResponse.FeatureCandidate::score).reversed())
                .limit(5)
                .toList();
    }

    private GitHubProjectAnalysisResponse.ProjectProfile projectProfile(
            GitHubRepositorySnapshot snapshot,
            List<GitHubProjectAnalysisResponse.CoreFile> coreFiles,
            List<GitHubProjectAnalysisResponse.IntegrationFact> integrations
    ) {
        String content = allContent(snapshot);
        List<String> evidence = coreFiles.stream().map(GitHubProjectAnalysisResponse.CoreFile::path).limit(4).toList();
        String repositoryDescription = blankToNull(snapshot.description());
        boolean hasSpring = content.contains("spring-boot") || content.contains("@restcontroller");
        boolean hasReact = content.contains("\"react\"") || content.contains("react-router");
        boolean hasJavaConsole = coreFiles.stream().anyMatch(file -> file.role().equals("애플리케이션 진입점"))
                && coreFiles.stream().allMatch(file -> file.role().equals("애플리케이션 진입점") || file.role().equals("애플리케이션 코드"));

        if (hasJavaConsole && !hasSpring && !hasReact && integrations.isEmpty()) {
            String summary = prefixDescription(repositoryDescription)
                    + "Java 클래스와 main() 메서드, 콘솔 출력 중심으로 구성된 소규모 예제 또는 실습 프로젝트로 보입니다. "
                    + "명확한 API·DB·서비스 계층은 분석 대상 코드에서 확인되지 않았습니다.";
            return new GitHubProjectAnalysisResponse.ProjectProfile(
                    "Java 콘솔 기반 예제·실습 프로젝트", summary, "LOW", evidence,
                    List.of("README 또는 저장소 설명만으로 실제 서비스 목적을 확정할 수 없습니다.", "파일 간 호출 관계나 도메인 기능이 명확히 드러나지 않아 각 클래스는 참고 코드로 분류했습니다.")
            );
        }

        String classification;
        if (hasSpring && hasReact) classification = "프론트엔드·백엔드 분리형 웹 프로젝트";
        else if (hasSpring) classification = "Spring 기반 백엔드 또는 웹 API 프로젝트";
        else if (hasReact) classification = "React 기반 웹 프론트엔드 프로젝트";
        else classification = "소스 구조 기반 소프트웨어 프로젝트";
        String summary = prefixDescription(repositoryDescription)
                + classification + "로 분석되었습니다. "
                + coreFiles.stream().filter(file -> file.importance().equals("CORE")).count()
                + "개의 핵심 구조 파일과 " + integrations.size() + "개의 연동·인프라 신호를 확인했습니다.";
        List<String> limitations = integrations.isEmpty()
                ? List.of("분석 대상 파일에서 외부 API·AI·메시지 큐 같은 연동 코드는 확인되지 않았습니다.")
                : List.of("연동 사실은 코드 신호로 확인했지만, 실제 API 호출 성공 여부나 운영 상태는 실행·테스트 없이는 판단할 수 없습니다.");
        return new GitHubProjectAnalysisResponse.ProjectProfile(
                classification, summary, hasSpring || hasReact ? "MEDIUM" : "LOW", evidence, limitations
        );
    }

    private List<GitHubProjectAnalysisResponse.ArchitectureLayer> architecture(
            List<GitHubProjectAnalysisResponse.CoreFile> coreFiles,
            List<GitHubRepositorySnapshot.RepositoryFile> analyzedFiles
    ) {
        List<GitHubProjectAnalysisResponse.ArchitectureLayer> layers = new ArrayList<>();
        List<String> frontend = pathsByPrefix(analyzedFiles, "frontend/");
        if (!frontend.isEmpty()) layers.add(new GitHubProjectAnalysisResponse.ArchitectureLayer("프론트엔드", "페이지와 기능 모듈이 사용자 화면 계층을 구성합니다.", frontend));
        List<String> controllers = pathsByRole(coreFiles, "API 컨트롤러");
        if (!controllers.isEmpty()) layers.add(new GitHubProjectAnalysisResponse.ArchitectureLayer("API", "컨트롤러 또는 라우터가 HTTP 요청을 받습니다.", controllers));
        List<String> services = pathsByRole(coreFiles, "비즈니스 서비스");
        if (!services.isEmpty()) layers.add(new GitHubProjectAnalysisResponse.ArchitectureLayer("비즈니스 로직", "서비스가 도메인 처리와 흐름 제어를 담당합니다.", services));
        List<String> persistence = coreFiles.stream()
                .filter(file -> file.role().equals("데이터 접근") || file.role().equals("도메인 모델"))
                .map(GitHubProjectAnalysisResponse.CoreFile::path).toList();
        if (!persistence.isEmpty()) layers.add(new GitHubProjectAnalysisResponse.ArchitectureLayer("데이터", "저장소와 도메인 모델이 데이터 처리 책임을 분리합니다.", persistence));
        List<String> entryPoints = pathsByRole(coreFiles, "애플리케이션 진입점");
        if (!entryPoints.isEmpty()) layers.add(new GitHubProjectAnalysisResponse.ArchitectureLayer("실행 진입점", "프로그램 또는 애플리케이션이 시작되는 지점입니다.", entryPoints));
        return layers;
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
        if (value.contains("@restcontroller") || value.contains("@controller") || value.contains("@getmapping") || value.contains("@postmapping") || path.toLowerCase(Locale.ROOT).contains("router")) return "API 컨트롤러";
        if (value.contains("@service") || path.toLowerCase(Locale.ROOT).contains("service")) return "비즈니스 서비스";
        if (containsAny(value, "resttemplate", "webclient", "feignclient", "axios", "fetch(", "@google/genai", "openai")) return "외부 API 클라이언트";
        if (containsAny(value, "spring-security", "jjwt", "@preauthorize")) return "인증·권한 처리";
        if (value.contains("jparepository") || path.toLowerCase(Locale.ROOT).contains("repository")) return "데이터 접근";
        if (value.contains("@entity") || path.toLowerCase(Locale.ROOT).contains("entity")) return "도메인 모델";
        if (path.endsWith(".java") && JAVA_EXTENDS.matcher(content).find()) return "상속 확장 클래스";
        if (path.endsWith(".java") && JAVA_IMPLEMENTS.matcher(content).find()) return "인터페이스 구현 클래스";
        if (path.endsWith(".java") && content.contains("interface ")) return "인터페이스 정의";
        if (value.contains("@springbootapplication") || value.contains("static void main")) return "애플리케이션 진입점";
        if (path.toLowerCase(Locale.ROOT).contains("page")) return "화면";
        if (path.toLowerCase(Locale.ROOT).contains("component")) return "UI 컴포넌트";
        return "애플리케이션 코드";
    }

    private int score(String path, String content) {
        String value = (path + "\n" + content).toLowerCase(Locale.ROOT);
        int score = 0;
        if (value.contains("@restcontroller") || value.contains("@getmapping") || value.contains("@postmapping") || path.toLowerCase(Locale.ROOT).contains("router")) score += 10;
        if (value.contains("@service")) score += 9;
        if (containsAny(value, "resttemplate", "webclient", "feignclient", "axios", "fetch(", "@google/genai", "openai")) score += 9;
        if (containsAny(value, "spring-security", "jjwt", "@preauthorize")) score += 8;
        if (value.contains("jparepository")) score += 7;
        if (value.contains("@entity")) score += 6;
        if (JAVA_EXTENDS.matcher(content).find() || JAVA_IMPLEMENTS.matcher(content).find()) score += 4;
        if (value.contains("@springbootapplication") || value.contains("static void main")) score += 5;
        if (value.contains("createbrowserrouter") || value.contains("route")) score += 5;
        if (path.toLowerCase(Locale.ROOT).contains("page")) score += 4;
        return score;
    }

    private String importance(int score) {
        if (score >= 8) return "CORE";
        if (score >= 4) return "STRUCTURAL";
        return "REFERENCE";
    }

    private String staticResponsibility(String role, GitHubRepositorySnapshot.RepositoryFile file) {
        String symbol = symbols(file).getFirst();
        if (role.equals("상속 확장 클래스")) {
            return symbol + "가 " + javaExtendedType(file.content()) + "를 상속해 상위 타입의 구조를 확장합니다.";
        }
        if (role.equals("인터페이스 구현 클래스")) {
            return symbol + "가 " + javaImplementedType(file.content()) + " 인터페이스의 동작을 구현합니다.";
        }
        if (role.equals("인터페이스 정의")) {
            return symbol + " 인터페이스가 구현 클래스가 따라야 할 공통 동작을 정의합니다.";
        }
        if (role.equals("애플리케이션 진입점")) {
            List<String> createdTypes = javaInstantiatedTypes(file.content());
            if (!createdTypes.isEmpty()) {
                return "main()에서 " + String.join(", ", createdTypes) + " 객체를 생성해 실행 흐름을 시작합니다.";
            }
        }
        return staticGeneralResponsibility(role);
    }

    private String staticGeneralResponsibility(String role) {
        return switch (role) {
            case "API 컨트롤러" -> "HTTP 요청을 받아 서비스 기능의 진입점 역할을 합니다.";
            case "비즈니스 서비스" -> "도메인 규칙과 여러 처리 단계를 조합하는 계층입니다.";
            case "외부 API 클라이언트" -> "외부 서비스 또는 백엔드 API와 통신하는 책임을 가집니다.";
            case "인증·권한 처리" -> "사용자 인증이나 접근 제어와 관련된 책임을 가집니다.";
            case "데이터 접근" -> "저장소 또는 ORM을 통해 데이터를 조회·저장하는 책임을 가집니다.";
            case "도메인 모델" -> "프로젝트가 다루는 핵심 데이터를 표현하는 모델입니다.";
            case "애플리케이션 진입점" -> "프로그램 또는 애플리케이션을 시작하는 진입점입니다.";
            case "화면" -> "사용자가 보는 기능 화면을 구성하는 책임을 가집니다.";
            case "UI 컴포넌트" -> "여러 화면에서 재사용될 수 있는 UI 구성 요소입니다.";
            default -> "정적 규칙으로 분류된 일반 애플리케이션 코드입니다.";
        };
    }

    private String selectionReason(String role, int score, GitHubRepositorySnapshot.RepositoryFile file) {
        String symbol = symbols(file).getFirst();
        if (role.equals("상속 확장 클래스")) {
            return symbol + " extends " + javaExtendedType(file.content()) + " 선언으로 상속 관계가 확인되어 선정했습니다.";
        }
        if (role.equals("인터페이스 구현 클래스")) {
            return symbol + " implements " + javaImplementedType(file.content()) + " 선언으로 다형성 구조가 확인되어 선정했습니다.";
        }
        if (role.equals("인터페이스 정의")) {
            return "인터페이스 선언이 확인되어 구현 클래스와의 공통 계약을 설명하는 근거로 선정했습니다.";
        }
        if (role.equals("애플리케이션 진입점") && !javaInstantiatedTypes(file.content()).isEmpty()) {
            return "main()에서 관련 객체를 생성해 실제 콘솔 실행 흐름을 시작하는 코드로 선정했습니다.";
        }
        return staticGeneralSelectionReason(role, score);
    }

    private String staticGeneralSelectionReason(String role, int score) {
        return switch (role) {
            case "API 컨트롤러" -> "HTTP 요청을 받는 API 진입점 또는 라우트 선언이 있어 사용자 기능 흐름의 시작점으로 선정했습니다.";
            case "비즈니스 서비스" -> "도메인 처리와 서비스 조합을 담당하는 코드 신호가 있어 기능 규칙의 핵심 근거로 선정했습니다.";
            case "외부 API 클라이언트" -> "외부 HTTP·AI SDK·API 호출 코드가 있어 서비스 연동 방식을 설명하는 핵심 근거로 선정했습니다.";
            case "인증·권한 처리" -> "사용자 인증 또는 접근 제어와 관련된 신호가 있어 보안 흐름 설명에 필요한 근거로 선정했습니다.";
            case "데이터 접근" -> "저장소 또는 JPA 접근 코드가 있어 데이터가 저장·조회되는 방식을 설명하는 근거로 선정했습니다.";
            case "도메인 모델" -> "핵심 데이터를 표현하는 엔티티 또는 모델 신호가 있어 도메인 구조 설명에 필요한 근거로 선정했습니다.";
            case "애플리케이션 진입점" -> "프로그램이 시작되는 main() 또는 애플리케이션 부트스트랩 코드가 있어 실행 구조를 설명하는 근거로 선정했습니다.";
            case "화면" -> "사용자 화면 단위 파일이어서 실제 사용자 흐름을 설명하는 구조 근거로 선정했습니다.";
            case "UI 컴포넌트" -> "재사용 UI 구성 요소를 나타내는 파일이어서 화면 구조를 설명하는 보조 근거로 선정했습니다.";
            default -> score == 0
                    ? "분석 가능한 소스 파일이지만 API·서비스·DB·연동처럼 핵심 기능을 확정할 구조 신호는 발견되지 않아 참고 코드로 분류했습니다."
                    : "프로젝트 구조를 보완 설명하는 코드 신호가 있어 참고 근거로 선정했습니다.";
        };
    }

    private String javaExtendedType(String content) {
        Matcher matcher = JAVA_EXTENDS.matcher(content);
        return matcher.find() ? matcher.group(2) : "상위 클래스";
    }

    private String javaImplementedType(String content) {
        Matcher matcher = JAVA_IMPLEMENTS.matcher(content);
        return matcher.find() ? matcher.group(2) : "인터페이스";
    }

    private List<String> javaInstantiatedTypes(String content) {
        LinkedHashMap<String, Boolean> types = new LinkedHashMap<>();
        Matcher matcher = JAVA_NEW.matcher(content);
        while (matcher.find() && types.size() < 4) types.put(matcher.group(1), true);
        return List.copyOf(types.keySet());
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

    // package/import 선언은 파일마다 거의 똑같아서 "주요 코드" 발췌로 보여줘도 아무 의미가
    // 없다는 문제(2026-08-14)를 고친 뒤에도, "파일 맨 위 몇 줄"이라는 접근 자체가 필드
    // 선언과 메서드 시그니처만 보여주고 실제 처리 로직(메서드 본문)은 안 보여준다는 지적을
    // 받았다 - 사실이었다. 그래서 컨트롤러 파일이면 @GetMapping류로 매핑된 첫 메서드를
    // 중괄호 짝을 맞춰 통째로 찾아서 그 본문까지 보여주도록 바꿨고(mappedMethodExcerpt),
    // 매핑 메서드가 없는 파일(서비스/엔티티 등)만 기존 "맨 위부터" 방식으로 대체한다.
    private static final java.util.Set<String> EXCERPT_SKIP_PREFIXES = java.util.Set.of("package ", "import ");
    private static final int MAX_EXCERPT_LINES = 14;
    private static final int MAX_EXCERPT_CHARS = 600;

    // 파일에서 매핑 메서드를 하나도 못 찾았을 때 쓰는 대체 발췌 - package/import 줄은
    // 건너뛰고 실제 선언부(어노테이션, 클래스/메서드 본문)부터 최대 MAX_EXCERPT_LINES줄을
    // 담는다.
    private String headExcerpt(String content) {
        List<String> candidateLines = content.lines().map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> EXCERPT_SKIP_PREFIXES.stream().noneMatch(line::startsWith))
                .limit(MAX_EXCERPT_LINES)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        trimIncompleteTrailingLines(candidateLines);
        return joinCapped(candidateLines);
    }

    private String excerptOf(List<GitHubProjectAnalysisResponse.MethodExcerpt> mappedMethods, String content) {
        return mappedMethods.isEmpty() ? headExcerpt(content) : mappedMethods.get(0).excerpt();
    }

    // @GetMapping/@PostMapping 등으로 매핑된 메서드를 "전부" 찾아, 각각 어노테이션 줄부터
    // 여는 중괄호와 짝이 맞는 닫는 중괄호까지 실제 메서드 본문을 통째로 잘라온다.
    //
    // 2026-08-14에 겪은 버그 두 가지를 여기서 같이 고쳤다.
    // (1) @PostMapping("/{boardId}/comments")처럼 스프링 경로 변수 문법 자체가 '{'/'}'를
    //     문자열 리터럴 안에 담고 있으면, 그 어노테이션 줄만 보고도 괄호 짝이 맞아버려서
    //     메서드 본문은 하나도 못 찾고 어노테이션 한 줄만 발췌되는 문제 - 중괄호를 찾거나
    //     셀 때는 큰따옴표 문자열 리터럴 내용을 공백으로 지운 버전(codeOnlyLines)을 쓴다.
    // (2) 예전엔 파일에서 매핑 메서드를 "첫 번째 하나만" 찾았는데(mappedMethodExcerpt),
    //     한 파일이 서로 다른 "구현" 여러 개의 근거로 쓰이면 전부 똑같은 코드(그 파일의
    //     첫 메서드)만 보여주는 문제가 있었다 - 이제 파일 안의 매핑 메서드를 전부 찾아
    //     메서드 이름별로 담아둬서, 포트폴리오 생성 시 구현별로 정확한 메서드를 골라 쓸 수
    //     있게 했다.
    private List<GitHubProjectAnalysisResponse.MethodExcerpt> allMappedMethodExcerpts(String content) {
        List<GitHubProjectAnalysisResponse.MethodExcerpt> results = new ArrayList<>();
        String[] rawLines = content.split("\n", -1);
        String[] codeOnlyLines = new String[rawLines.length];
        for (int i = 0; i < rawLines.length; i++) codeOnlyLines[i] = withoutStringLiterals(rawLines[i]);

        for (int i = 0; i < rawLines.length; i++) {
            if (!METHOD_MAPPING.matcher(rawLines[i]).find()) continue;
            int openLine = -1;
            for (int j = i; j < rawLines.length && j < i + 6; j++) {
                if (codeOnlyLines[j].contains("{")) { openLine = j; break; }
            }
            if (openLine < 0) continue;
            int depth = 0;
            int endLine = -1;
            outer:
            for (int j = openLine; j < rawLines.length; j++) {
                for (char character : codeOnlyLines[j].toCharArray()) {
                    if (character == '{') depth++;
                    else if (character == '}') {
                        depth--;
                        if (depth == 0) { endLine = j; break outer; }
                    }
                }
            }
            if (endLine < 0) continue;
            StringBuilder signature = new StringBuilder();
            for (int j = i; j <= openLine; j++) signature.append(codeOnlyLines[j]).append(' ');
            String methodName = methodNameOf(signature.toString());
            List<String> methodLines = new ArrayList<>();
            for (int j = i; j <= endLine; j++) {
                String trimmed = rawLines[j].trim();
                if (!trimmed.isEmpty()) methodLines.add(trimmed);
            }
            if (methodLines.size() > MAX_EXCERPT_LINES) {
                methodLines = new ArrayList<>(methodLines.subList(0, MAX_EXCERPT_LINES));
                trimIncompleteTrailingLines(methodLines);
            }
            if (methodName != null && !methodLines.isEmpty()) {
                results.add(new GitHubProjectAnalysisResponse.MethodExcerpt(methodName, joinCapped(methodLines)));
            }
            i = endLine;
        }
        return results;
    }

    // 메서드 시그니처 텍스트에서 실제 메서드 이름을 찾는다 - "@RequestParam(..." 처럼
    // 파라미터 어노테이션도 '(' 앞에 식별자가 오지만, 어노테이션은 항상 식별자 바로 앞에
    // '@'가 붙어있는 반면 진짜 메서드 이름 앞에는 '@'가 없다는 점으로 구분한다.
    private static final Pattern CALL_LIKE_IDENTIFIER = Pattern.compile("(?<!@)\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    private String methodNameOf(String signatureText) {
        Matcher matcher = CALL_LIKE_IDENTIFIER.matcher(signatureText);
        return matcher.find() ? matcher.group(1) : null;
    }

    // 큰따옴표로 감싸인 문자열 리터럴 내용을 공백으로 지운다(이스케이프된 \" 는 종료
    // 따옴표로 취급하지 않음) - 중괄호 탐색/카운팅이 문자열 안의 '{'/'}' 에 속지 않게 하기
    // 위한 용도라 주석 안의 따옴표까지 완벽히 처리하진 않지만, 이 코드베이스가 다루는
    // 어노테이션/시그니처 수준에서는 충분하다.
    private String withoutStringLiterals(String line) {
        StringBuilder result = new StringBuilder(line.length());
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
                result.append(' ');
            } else {
                result.append(inString ? ' ' : character);
            }
        }
        return result.toString();
    }

    // 줄 개수 제한(MAX_EXCERPT_LINES) 때문에 메서드 시그니처 중간이나 어노테이션 바로
    // 뒤(대상 선언 없이)에서 스니펫이 뚝 끊기는 문제를 겪었다(2026-08-14, "@GetMapping"만
    // 나오거나 "addComment(@PathVariable Long boardId," 에서 끊긴 사례). 끝 줄이
    // 미완성으로 보이면(쉼표/여는 괄호/점 등으로 끝나거나, 대상 없는 어노테이션이면)
    // 완결된 줄이 나올 때까지 뒤에서부터 제거한다.
    private void trimIncompleteTrailingLines(List<String> lines) {
        while (!lines.isEmpty() && looksIncomplete(lines.get(lines.size() - 1))) {
            lines.remove(lines.size() - 1);
        }
    }

    private boolean looksIncomplete(String line) {
        if (line.startsWith("@")) return true;
        char last = line.charAt(line.length() - 1);
        return last == ',' || last == '(' || last == '.' || last == '+' || last == '&' || last == '|' || last == '=';
    }

    // 글자 수 컷을 substring()으로 그냥 하면 "categoryM..." 처럼 단어 중간이 잘린다
    // (2026-08-14에 실제로 겪음) - MAX_EXCERPT_CHARS 이내에서 가장 마지막 줄바꿈까지만 자른다.
    private String joinCapped(List<String> lines) {
        String joined = String.join("\n", lines);
        if (joined.length() <= MAX_EXCERPT_CHARS) return joined;
        int cut = joined.lastIndexOf('\n', MAX_EXCERPT_CHARS);
        return (cut > 0 ? joined.substring(0, cut) : joined.substring(0, MAX_EXCERPT_CHARS)) + "\n...";
    }

    // HTTP 매핑 메서드 하나("@GetMapping("/attractions/{id}")" 등)에서 뽑아낸 진짜 신호.
    // featureCandidates()/implementationStories()가 역할 이름만 반복하는 대신 이 신호로
    // "이 기능이 실제로 무엇을 하는지"를 설명한다(2026-08-14).
    private record EndpointSignal(String verb, String path, String methodName) {
        String display() {
            return (path == null || path.isBlank() ? verb : verb + " " + path) + "(" + methodName + ")";
        }
    }

    private static final Pattern QUOTED_VALUE = Pattern.compile("\"([^\"]*)\"");

    // methodExcerpts()의 각 발췌는 항상 매핑 어노테이션 줄에서 시작한다(allMappedMethodExcerpts
    // 참고) - 그 첫 줄에서 HTTP 메서드와, 있으면 경로 문자열까지 뽑는다.
    private List<EndpointSignal> endpointSignals(List<GitHubProjectAnalysisResponse.CoreFile> files) {
        List<EndpointSignal> signals = new ArrayList<>();
        for (GitHubProjectAnalysisResponse.CoreFile file : files) {
            for (GitHubProjectAnalysisResponse.MethodExcerpt method : file.methodExcerpts()) {
                String firstLine = method.excerpt().lines().findFirst().orElse("");
                Matcher verbMatcher = METHOD_MAPPING.matcher(firstLine);
                if (!verbMatcher.find()) continue;
                String verb = verbMatcher.group(1).toUpperCase(Locale.ROOT);
                Matcher pathMatcher = QUOTED_VALUE.matcher(firstLine);
                String path = pathMatcher.find() ? pathMatcher.group(1) : "";
                signals.add(new EndpointSignal(verb, path, method.symbol()));
            }
        }
        return signals;
    }

    private String endpointNarrative(List<EndpointSignal> signals) {
        List<String> shown = signals.stream().map(EndpointSignal::display).distinct().limit(4).toList();
        int remaining = signals.size() - shown.size();
        String suffix = remaining > 0 ? " 외 " + remaining + "건" : "";
        return String.join(", ", shown) + suffix + " 등 총 " + signals.size() + "개의 API 엔드포인트가 이 기능의 진입점으로 확인되었습니다.";
    }

    private String mechanismNarrative(List<EndpointSignal> signals) {
        Map<String, Integer> verbCounts = new LinkedHashMap<>();
        for (EndpointSignal signal : signals) verbCounts.merge(koreanVerb(signal.verb()), 1, Integer::sum);
        String breakdown = verbCounts.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue() + "건")
                .reduce((left, right) -> left + ", " + right).orElse("");
        return breakdown + "의 HTTP 요청을 받아 서비스 계층에 위임해 처리하는 구조로 구현되어 있습니다.";
    }

    private String koreanVerb(String verb) {
        return switch (verb) {
            case "GET" -> "조회(GET)";
            case "POST" -> "등록(POST)";
            case "PUT" -> "수정(PUT)";
            case "DELETE" -> "삭제(DELETE)";
            case "PATCH" -> "부분 수정(PATCH)";
            default -> verb;
        };
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
        return filename + " 관련 구현";
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

    private List<String> pathsByPrefix(List<GitHubRepositorySnapshot.RepositoryFile> files, String prefix) {
        return files.stream().map(GitHubRepositorySnapshot.RepositoryFile::path).filter(path -> path.startsWith(prefix)).limit(3).toList();
    }

    private List<String> pathsByRole(List<GitHubProjectAnalysisResponse.CoreFile> files, String role) {
        return files.stream().filter(file -> file.role().equals(role)).map(GitHubProjectAnalysisResponse.CoreFile::path).toList();
    }

    private String allContent(GitHubRepositorySnapshot snapshot) {
        return snapshot.analyzedFiles().stream()
                .map(GitHubRepositorySnapshot.RepositoryFile::content)
                .reduce("", (left, right) -> left + "\n" + right)
                .toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String source, String... values) {
        String normalized = source.toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(values).anyMatch(value -> normalized.contains(value.toLowerCase(Locale.ROOT)));
    }

    private String prefixDescription(String description) {
        return blankToNull(description) == null ? "" : "저장소 설명에 따르면 \"" + description + "\". ";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record TechnologyEvidence(String category, List<String> evidence) {
    }
}

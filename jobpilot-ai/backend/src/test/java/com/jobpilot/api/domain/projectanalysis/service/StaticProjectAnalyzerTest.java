package com.jobpilot.api.domain.projectanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StaticProjectAnalyzerTest {
    private final StaticProjectAnalyzer analyzer = new StaticProjectAnalyzer();

    @Test
    void extractsTechnologyAndFeatureEvidenceWithoutAnAiCall() {
        GitHubRepositorySnapshot snapshot = new GitHubRepositorySnapshot(
                new GitHubRepositoryReference("example", "jobpilot", "https://github.com/example/jobpilot"),
                "main",
                "",
                Map.of("Java", 8_000, "TypeScript", 5_000),
                List.of(
                        new GitHubRepositorySnapshot.RepositoryFile("backend/pom.xml", "<artifactId>spring-boot-starter-data-jpa</artifactId><artifactId>mysql</artifactId>", 120),
                        new GitHubRepositorySnapshot.RepositoryFile("frontend/package.json", "{\"react\":\"18\",\"vite\":\"5\"}", 80),
                        new GitHubRepositorySnapshot.RepositoryFile("backend/src/main/java/example/matching/MatchingController.java", "@RestController @GetMapping class MatchingController {}", 100),
                        new GitHubRepositorySnapshot.RepositoryFile("backend/src/main/java/example/matching/MatchingService.java", "@Service class MatchingService {}", 80)
                ),
                List.of(
                        new GitHubRepositorySnapshot.RepositoryFile("backend/pom.xml", "<artifactId>spring-boot-starter-data-jpa</artifactId><artifactId>mysql</artifactId>", 120),
                        new GitHubRepositorySnapshot.RepositoryFile("frontend/package.json", "{\"react\":\"18\",\"vite\":\"5\"}", 80),
                        new GitHubRepositorySnapshot.RepositoryFile("backend/src/main/java/example/matching/MatchingController.java", "@RestController @GetMapping class MatchingController {}", 100),
                        new GitHubRepositorySnapshot.RepositoryFile("backend/src/main/java/example/matching/MatchingService.java", "@Service class MatchingService {}", 80)
                )
        );

        var result = analyzer.analyze(snapshot);

        assertThat(result.summarySource()).isEqualTo("STATIC");
        assertThat(result.technologyStack()).extracting("name")
                .contains("Spring Data JPA", "MySQL", "React", "Java", "TypeScript");
        assertThat(result.featureCandidates()).isNotEmpty();
        assertThat(result.featureCandidates().get(0).evidence())
                .allMatch(path -> path.contains("matching"));
        assertThat(result.coreFiles()).allMatch(file -> !file.selectionReason().isBlank());
        assertThat(result.coreFiles()).anyMatch(file -> file.importance().equals("CORE"));
        assertThat(result.implementations()).isNotEmpty();
        assertThat(result.implementations()).allSatisfy(implementation ->
                assertThat(implementation.evidence()).allSatisfy(evidence ->
                        assertThat(evidence.path()).isIn(result.coreFiles().stream().map(file -> file.path()).toList())
                )
        );
    }

    @Test
    void treatsSimpleMainClassesAsStructureNotAsInventedBusinessFeatures() {
        List<GitHubRepositorySnapshot.RepositoryFile> files = List.of(
                new GitHubRepositorySnapshot.RepositoryFile("src/Cat.java", "public class Cat { public static void main(String[] args) { System.out.println(\"cat\"); } }", 100),
                new GitHubRepositorySnapshot.RepositoryFile("src/Main.java", "public class Main { public static void main(String[] args) { System.out.println(\"main\"); } }", 100)
        );
        GitHubRepositorySnapshot snapshot = new GitHubRepositorySnapshot(
                new GitHubRepositoryReference("example", "practice", "https://github.com/example/practice"),
                "main", "", Map.of("Java", 1_000), files, files
        );

        var result = analyzer.analyze(snapshot);

        assertThat(result.projectProfile().classification()).contains("실습");
        assertThat(result.featureCandidates()).singleElement()
                .satisfies(candidate -> assertThat(candidate.id()).isEqualTo("structure-review"));
        assertThat(result.coreFiles()).allMatch(file -> file.importance().equals("STRUCTURAL"));
    }

    @Test
    void mergesAiExplanationOnlyWhenItReferencesKnownCoreFiles() {
        List<GitHubRepositorySnapshot.RepositoryFile> files = List.of(
                new GitHubRepositorySnapshot.RepositoryFile(
                        "src/main/java/example/OrderController.java",
                        "@RestController class OrderController { @GetMapping String orders() { return \"ok\"; } }", 100
                )
        );
        GitHubRepositorySnapshot snapshot = new GitHubRepositorySnapshot(
                new GitHubRepositoryReference("example", "orders", "https://github.com/example/orders"),
                "main", "", Map.of("Java", 1_000), files, files
        );
        var staticResult = analyzer.analyze(snapshot);
        String path = staticResult.coreFiles().get(0).path();
        var aiSummary = new GeminiProjectSummaryClient.AiSummary(
                "주문 조회 HTTP 진입점을 포함한 저장소입니다.",
                "컨트롤러가 조회 요청을 받는 코드가 확인됩니다.",
                "Spring HTTP API 예제", "OrderController의 매핑 메서드가 확인됩니다.",
                Map.of(path, new GeminiProjectSummaryClient.FileExplanation(
                        "주문 조회 요청을 받는 HTTP 진입점입니다.",
                        "@GetMapping 메서드가 사용자 요청을 처리하므로 발표에서 API 시작점으로 설명할 수 있습니다.", "CORE"
                )),
                List.of(new GeminiProjectSummaryClient.AiCodeFlow(
                        "주문 조회 요청", "HTTP 요청이 컨트롤러의 매핑 메서드로 들어오는 흐름입니다.", List.of(path), "MEDIUM"
                )),
                List.of(new GeminiProjectSummaryClient.AiImplementation(
                        "주문 조회 API", "주문 조회 HTTP 요청을 처리하는 구현입니다.", "컨트롤러의 매핑 메서드가 요청을 받습니다.",
                        List.of("Java"), List.of(new GeminiProjectSummaryClient.AiCodeEvidence(
                                path, "OrderController", "요청을 받는 컨트롤러 클래스입니다."
                        ))
                )),
                Map.of(staticResult.featureCandidates().get(0).id(), "컨트롤러 매핑 코드가 확인됩니다.")
        );

        var result = analyzer.applyAiSummary(staticResult, aiSummary);

        assertThat(result.summarySource()).isEqualTo("GEMINI");
        assertThat(result.coreFiles().get(0).responsibility()).contains("HTTP");
        assertThat(result.codeFlows()).singleElement()
                .satisfies(flow -> assertThat(flow.evidence()).containsExactly(path));
        assertThat(result.implementations()).singleElement()
                .satisfies(implementation -> assertThat(implementation.evidence().get(0).path()).isEqualTo(path));
    }

    @Test
    void givesAiSelectedReadingPathsPriorityInCoreEvidence() {
        List<GitHubRepositorySnapshot.RepositoryFile> files = List.of(
                new GitHubRepositorySnapshot.RepositoryFile("pkg/services/check_update.py", "def check_update(): pass", 100),
                new GitHubRepositorySnapshot.RepositoryFile("pkg/api/pygwalker.py", "class PygWalker: pass", 100)
        );
        GitHubRepositorySnapshot snapshot = new GitHubRepositorySnapshot(
                new GitHubRepositoryReference("example", "library", "https://github.com/example/library"),
                "main", "", Map.of("Python", 1_000), files, files
        );

        var result = analyzer.analyze(snapshot, List.of("pkg/api/pygwalker.py"));

        assertThat(result.coreFiles().get(0).path()).isEqualTo("pkg/api/pygwalker.py");
    }

    @Test
    void parsesOnlyGithubOwnerAndRepositoryUrls() {
        var reference = GitHubRepositoryReference.parse("https://github.com/openai/example-repo.git");

        assertThat(reference.owner()).isEqualTo("openai");
        assertThat(reference.repository()).isEqualTo("example-repo");
    }

    @Test
    void usesOneJsonResponseFormatObjectForGeminiInteractions() {
        var request = new ObjectMapper().createObjectNode();

        GeminiProjectSummaryClient.jsonResponseFormat(request)
                .put("type", "text")
                .put("mime_type", "application/json");

        assertThat(request.path("response_format").isObject()).isTrue();
        assertThat(request.path("response_format").path("mime_type").asText()).isEqualTo("application/json");
    }

    @Test
    void describesJavaInheritanceAndEntryPointRelationships() {
        List<GitHubRepositorySnapshot.RepositoryFile> files = List.of(
                new GitHubRepositorySnapshot.RepositoryFile("src/demo/Monster.java", "class Monster {}", 40),
                new GitHubRepositorySnapshot.RepositoryFile("src/demo/AttackMonster.java", "class AttackMonster extends Monster {}", 70),
                new GitHubRepositorySnapshot.RepositoryFile("src/demo/MonsterMain.java", "class MonsterMain { static void main(String[] args) { new AttackMonster(); } }", 100)
        );
        GitHubRepositorySnapshot snapshot = new GitHubRepositorySnapshot(
                new GitHubRepositoryReference("example", "practice", "https://github.com/example/practice"),
                "main", "", Map.of("Java", 1_000), files, files
        );

        var result = analyzer.analyze(snapshot, List.of("src/demo/AttackMonster.java", "src/demo/MonsterMain.java"));

        assertThat(result.coreFiles()).anySatisfy(file -> {
            assertThat(file.path()).isEqualTo("src/demo/AttackMonster.java");
            assertThat(file.role()).isEqualTo("상속 확장 클래스");
            assertThat(file.responsibility()).contains("Monster");
            assertThat(file.selectionReason()).contains("extends Monster");
        });
        assertThat(result.coreFiles()).anySatisfy(file -> {
            assertThat(file.path()).isEqualTo("src/demo/MonsterMain.java");
            assertThat(file.responsibility()).contains("AttackMonster");
        });
    }

    @Test
    void expandsPlannerSelectionsWithRelatedSourceFilesInTheSamePackage() {
        List<GitHubRepositorySnapshot.RepositoryFile> files = List.of(
                new GitHubRepositorySnapshot.RepositoryFile("src/demo/Monster.java", "class Monster {}", 40),
                new GitHubRepositorySnapshot.RepositoryFile("src/demo/AttackMonster.java", "class AttackMonster extends Monster {}", 70),
                new GitHubRepositorySnapshot.RepositoryFile("src/demo/DefenseMonster.java", "class DefenseMonster extends Monster {}", 70),
                new GitHubRepositorySnapshot.RepositoryFile("src/other/OtherMain.java", "class OtherMain {}", 40)
        );
        GitHubRepositorySnapshot snapshot = new GitHubRepositorySnapshot(
                new GitHubRepositoryReference("example", "practice", "https://github.com/example/practice"),
                "main", "", Map.of("Java", 1_000), files, files
        );

        var client = new GitHubRepositoryClient(new ObjectMapper(), "");
        var expanded = client.expandFocusPaths(snapshot, List.of("src/demo/AttackMonster.java"));

        assertThat(expanded).contains("src/demo/AttackMonster.java", "src/demo/Monster.java", "src/demo/DefenseMonster.java");
        assertThat(expanded).doesNotContain("src/other/OtherMain.java");
    }
}

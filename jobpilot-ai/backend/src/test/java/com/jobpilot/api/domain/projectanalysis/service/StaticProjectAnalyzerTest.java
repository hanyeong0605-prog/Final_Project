package com.jobpilot.api.domain.projectanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    @Test
    void parsesOnlyGithubOwnerAndRepositoryUrls() {
        var reference = GitHubRepositoryReference.parse("https://github.com/openai/example-repo.git");

        assertThat(reference.owner()).isEqualTo("openai");
        assertThat(reference.repository()).isEqualTo("example-repo");
    }
}

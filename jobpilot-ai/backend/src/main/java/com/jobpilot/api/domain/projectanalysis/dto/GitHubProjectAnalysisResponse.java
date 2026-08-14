package com.jobpilot.api.domain.projectanalysis.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GitHubProjectAnalysisResponse(
        RepositoryInfo repository,
        String overview,
        String aiNarrative,
        String summarySource,
        ProjectProfile projectProfile,
        List<TechnologyFact> technologyStack,
        List<ArchitectureLayer> architecture,
        List<IntegrationFact> integrations,
        List<CodeFlow> codeFlows,
        List<ImplementationStory> implementations,
        List<FeatureCandidate> featureCandidates,
        List<CoreFile> coreFiles,
        AnalysisMetrics metrics,
        List<String> notices,
        String generatedOutput
) {
    public record RepositoryInfo(
            String owner,
            String name,
            String fullName,
            String htmlUrl,
            String defaultBranch,
            String description,
            Instant analyzedAt
    ) {
    }

    public record TechnologyFact(String name, String category, List<String> evidence) {
    }

    public record ProjectProfile(
            String classification,
            String summary,
            String confidence,
            List<String> evidence,
            List<String> limitations
    ) {
    }

    public record ArchitectureLayer(String name, String description, List<String> evidence) {
    }

    public record IntegrationFact(
            String name,
            String category,
            String direction,
            String description,
            List<String> evidence
    ) {
    }

    public record CodeFlow(
            String title,
            String description,
            List<String> evidence,
            String confidence
    ) {
    }

    public record ImplementationStory(
            String id,
            String title,
            String description,
            String mechanism,
            List<String> technologies,
            List<CodeEvidence> evidence
    ) {
    }

    public record CodeEvidence(
            String path,
            String symbol,
            String description
    ) {
    }

    public record FeatureCandidate(
            String id,
            String title,
            String description,
            String confidence,
            List<String> evidence,
            int score
    ) {
    }

    // methodExcerpts: 파일 안에 HTTP 매핑 메서드가 여러 개 있을 때(예: 컨트롤러 하나에
    // 엔드포인트가 여러 개), 이 파일이 서로 다른 "구현" 여러 개의 근거로 쓰이면 각 구현이
    // 실제로 가리키는 메서드가 다를 수 있다(2026-08-14) - excerpt는 기존처럼 "대표 발췌"
    // 하나만 담고, 이 파일에서 찾은 메서드별 발췌를 전부 methodExcerpts에 담아둬서 포트폴리오
    // 생성 시 구현별로 정확한 메서드를 골라 보여줄 수 있게 한다.
    public record CoreFile(
            String path,
            String role,
            String responsibility,
            List<String> symbols,
            String excerpt,
            int score,
            String importance,
            String selectionReason,
            List<MethodExcerpt> methodExcerpts
    ) {
    }

    public record MethodExcerpt(String symbol, String excerpt) {
    }

    public record AnalysisMetrics(int totalFiles, int sourceFiles, int analyzedFiles, Map<String, Integer> fileTypes) {
    }
}

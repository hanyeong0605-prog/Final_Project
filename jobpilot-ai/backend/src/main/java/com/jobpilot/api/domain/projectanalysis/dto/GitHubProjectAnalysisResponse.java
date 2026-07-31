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

    public record CoreFile(
            String path,
            String role,
            String responsibility,
            List<String> symbols,
            String excerpt,
            int score,
            String importance,
            String selectionReason
    ) {
    }

    public record AnalysisMetrics(int totalFiles, int sourceFiles, int analyzedFiles, Map<String, Integer> fileTypes) {
    }
}

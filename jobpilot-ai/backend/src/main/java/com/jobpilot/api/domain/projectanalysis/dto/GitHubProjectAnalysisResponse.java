package com.jobpilot.api.domain.projectanalysis.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GitHubProjectAnalysisResponse(
        RepositoryInfo repository,
        String overview,
        String aiNarrative,
        String summarySource,
        List<TechnologyFact> technologyStack,
        List<ArchitectureLayer> architecture,
        List<FeatureCandidate> featureCandidates,
        List<CoreFile> coreFiles,
        AnalysisMetrics metrics,
        List<String> notices
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

    public record ArchitectureLayer(String name, String description, List<String> evidence) {
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
            List<String> symbols,
            String excerpt,
            int score
    ) {
    }

    public record AnalysisMetrics(int totalFiles, int sourceFiles, int analyzedFiles, Map<String, Integer> fileTypes) {
    }
}

package com.jobpilot.api.domain.projectanalysis.service;

import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GitHubProjectAnalysisService {
    private final GitHubRepositoryClient gitHubRepositoryClient;
    private final StaticProjectAnalyzer staticProjectAnalyzer;
    private final GeminiProjectSummaryClient geminiProjectSummaryClient;

    GitHubProjectAnalysisService(
            GitHubRepositoryClient gitHubRepositoryClient,
            StaticProjectAnalyzer staticProjectAnalyzer,
            GeminiProjectSummaryClient geminiProjectSummaryClient
    ) {
        this.gitHubRepositoryClient = gitHubRepositoryClient;
        this.staticProjectAnalyzer = staticProjectAnalyzer;
        this.geminiProjectSummaryClient = geminiProjectSummaryClient;
    }

    public GitHubProjectAnalysisResponse analyze(String repositoryUrl) {
        GitHubRepositorySnapshot initialSnapshot = gitHubRepositoryClient.load(repositoryUrl);
        List<String> plannedFocusPaths = geminiProjectSummaryClient.planCodeReading(initialSnapshot)
                .map(GeminiProjectSummaryClient.CodeReadingPlan::focusPaths)
                .orElse(List.of());
        List<String> focusPaths = gitHubRepositoryClient.expandFocusPaths(initialSnapshot, plannedFocusPaths);
        GitHubRepositorySnapshot snapshot = focusPaths.isEmpty()
                ? initialSnapshot
                : gitHubRepositoryClient.enrichWithFocusFiles(initialSnapshot, focusPaths);
        GitHubProjectAnalysisResponse staticAnalysis = staticProjectAnalyzer.analyze(snapshot, focusPaths);
        GeminiProjectSummaryClient.GeminiSummaryResult result = geminiProjectSummaryClient
                .summarize(staticAnalysis, snapshot, focusPaths);
        if (result.summary().isPresent()) {
            return staticProjectAnalyzer.applyAiSummary(staticAnalysis, result.summary().get());
        }
        return result.requested()
                ? staticProjectAnalyzer.markGeminiFallback(staticAnalysis, result.generatedOutput())
                : staticAnalysis;
    }
}

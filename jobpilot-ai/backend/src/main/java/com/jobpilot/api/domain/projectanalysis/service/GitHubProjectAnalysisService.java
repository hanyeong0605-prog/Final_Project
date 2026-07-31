package com.jobpilot.api.domain.projectanalysis.service;

import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse;
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
        GitHubRepositorySnapshot snapshot = gitHubRepositoryClient.load(repositoryUrl);
        GitHubProjectAnalysisResponse staticAnalysis = staticProjectAnalyzer.analyze(snapshot);
        return geminiProjectSummaryClient.summarize(staticAnalysis, snapshot)
                .map(summary -> staticProjectAnalyzer.applyAiSummary(staticAnalysis, summary))
                .orElse(staticAnalysis);
    }
}

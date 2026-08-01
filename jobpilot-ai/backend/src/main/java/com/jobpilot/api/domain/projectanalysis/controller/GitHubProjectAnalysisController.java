package com.jobpilot.api.domain.projectanalysis.controller;

import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisRequest;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse;
import com.jobpilot.api.domain.projectanalysis.service.GitHubProjectAnalysisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/project-analysis")
public class GitHubProjectAnalysisController {
    private final GitHubProjectAnalysisService projectAnalysisService;

    public GitHubProjectAnalysisController(GitHubProjectAnalysisService projectAnalysisService) {
        this.projectAnalysisService = projectAnalysisService;
    }

    @PostMapping("/github")
    public GitHubProjectAnalysisResponse analyze(@Valid @RequestBody GitHubProjectAnalysisRequest request) {
        return projectAnalysisService.analyze(request.repositoryUrl());
    }
}

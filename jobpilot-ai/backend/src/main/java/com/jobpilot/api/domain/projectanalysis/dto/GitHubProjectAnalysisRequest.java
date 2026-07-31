package com.jobpilot.api.domain.projectanalysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GitHubProjectAnalysisRequest(
        @NotBlank(message = "Enter a GitHub repository URL.")
        @Size(max = 500, message = "The GitHub repository URL is too long.")
        String repositoryUrl
) {
}

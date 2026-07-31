package com.jobpilot.api.domain.projectanalysis.service;

import com.jobpilot.api.domain.projectanalysis.exception.ProjectAnalysisException;
import java.net.URI;
import java.util.regex.Pattern;

record GitHubRepositoryReference(String owner, String repository, String htmlUrl) {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_.-]+");

    static GitHubRepositoryReference parse(String repositoryUrl) {
        try {
            URI uri = URI.create(repositoryUrl.trim());
            String host = uri.getHost();
            if (host == null || !(host.equalsIgnoreCase("github.com") || host.equalsIgnoreCase("www.github.com"))) {
                throw new ProjectAnalysisException("Enter a repository URL from github.com.");
            }

            String[] rawSegments = uri.getPath().split("/");
            if (rawSegments.length < 3 || rawSegments[1].isBlank() || rawSegments[2].isBlank()) {
                throw new ProjectAnalysisException("Enter a repository URL in the owner/repository format.");
            }

            String owner = rawSegments[1];
            String repository = rawSegments[2].replaceFirst("\\.git$", "");
            if (!SEGMENT.matcher(owner).matches() || !SEGMENT.matcher(repository).matches()) {
                throw new ProjectAnalysisException("The GitHub repository URL is not valid.");
            }
            return new GitHubRepositoryReference(owner, repository, "https://github.com/" + owner + "/" + repository);
        } catch (IllegalArgumentException exception) {
            throw new ProjectAnalysisException("Enter a valid GitHub repository URL.");
        }
    }
}

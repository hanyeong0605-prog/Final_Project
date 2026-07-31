package com.jobpilot.api.domain.projectanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

record GitHubRepositorySnapshot(
        GitHubRepositoryReference reference,
        String defaultBranch,
        String description,
        Map<String, Integer> languageBytes,
        List<RepositoryFile> allFiles,
        List<RepositoryFile> analyzedFiles
) {
    record RepositoryFile(String path, String content, long size) {
        String filename() {
            int separator = path.lastIndexOf('/');
            return separator >= 0 ? path.substring(separator + 1) : path;
        }

        String extension() {
            String filename = filename();
            int dot = filename.lastIndexOf('.');
            return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
        }
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }
}

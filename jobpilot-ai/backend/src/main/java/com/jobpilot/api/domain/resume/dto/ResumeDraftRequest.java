package com.jobpilot.api.domain.resume.dto;

import java.util.List;

/**
 * templateKey is deliberately a small, server-controlled enum-like value.
 * It prevents a client from injecting a document layout while still allowing
 * members to choose the structure they want for their first draft.
 */
public record ResumeDraftRequest(String title, String additionalRequest, String templateKey, List<String> answers, List<String> enabledSections) {}

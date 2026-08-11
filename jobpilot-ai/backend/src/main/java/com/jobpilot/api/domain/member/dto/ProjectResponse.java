package com.jobpilot.api.domain.member.dto;

import java.time.LocalDate;

public record ProjectResponse(
        Long id,
        String title,
        String roleDescription,
        String problemDescription,
        String solutionDescription,
        String resultDescription,
        String githubUrl,
        String deploymentUrl,
        LocalDate startedAt,
        LocalDate endedAt
) {}

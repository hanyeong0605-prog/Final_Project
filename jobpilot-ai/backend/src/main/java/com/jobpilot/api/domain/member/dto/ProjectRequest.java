package com.jobpilot.api.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// STAR(상황/역할 - 과제/문제 - 해결 - 결과) 구조를 그대로 필드로 나눴다 - 4개 다
// 필수는 아니다(질문식 작성 도중 일부만 채운 채로 임시 저장할 수 있어야 하므로).
public record ProjectRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 4000) String roleDescription,
        @Size(max = 4000) String problemDescription,
        @Size(max = 4000) String solutionDescription,
        @Size(max = 4000) String resultDescription,
        @Size(max = 1000) String githubUrl,
        @Size(max = 1000) String deploymentUrl,
        LocalDate startedAt,
        LocalDate endedAt
) {}

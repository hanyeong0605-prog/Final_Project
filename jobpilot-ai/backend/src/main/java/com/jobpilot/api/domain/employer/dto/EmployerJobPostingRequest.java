package com.jobpilot.api.domain.employer.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * companyName은 요청에 없다 - 서버가 로그인된 기업회원의 EmployerAccount.companyName을
 * 그대로 써서 저장한다(다른 회사 이름으로 공고를 올리는 것을 막기 위함).
 */
public record EmployerJobPostingRequest(
        @NotBlank String title,
        String companyUrl,
        @NotBlank String description,
        String location,
        String employmentType,
        String experienceType,
        String salary,
        LocalDateTime deadlineAt,
        boolean rollingDeadline
) {}

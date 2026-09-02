package com.jobpilot.api.domain.employer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
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
        String qualifications,
        String preferredQualifications,
        String imageUrl,
        LocalDateTime deadlineAt,
        boolean rollingDeadline
) {
    @AssertTrue(message = "상시 채용이 아니면 마감일시를 입력해 주세요.")
    public boolean isDeadlineValid() {
        return rollingDeadline || deadlineAt != null;
    }
}

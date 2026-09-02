package com.jobpilot.api.domain.employer.dto;

import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import java.time.LocalDateTime;

public record EmployerJobPostingResponse(
        Long id, String title, String companyName, String companyUrl, String description, String location,
        String employmentType, String experienceType, String salary, LocalDateTime deadlineAt,
        boolean rollingDeadline, String qualifications, String preferredQualifications, String imageUrl,
        String status, LocalDateTime publishedAt, long viewCount
) {
    public static EmployerJobPostingResponse from(JobPosting posting) {
        return new EmployerJobPostingResponse(
                posting.getId(), posting.getTitle(), posting.getCompanyName(), posting.getCompanyUrl(),
                posting.getDescription(), posting.getLocation(), posting.getEmploymentType(), posting.getExperienceType(),
                posting.getSalary(), posting.getDeadlineAt(), posting.isRollingDeadline(), posting.getEmployerQualifications(),
                posting.getEmployerPreferredQualifications(), posting.getEmployerImageUrl(), posting.getStatus(),
                posting.getPublishedAt(), posting.getViewCount());
    }
}

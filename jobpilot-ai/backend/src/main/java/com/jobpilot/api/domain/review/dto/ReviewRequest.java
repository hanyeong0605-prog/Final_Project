package com.jobpilot.api.domain.review.dto;

import jakarta.validation.constraints.*;

/** Fields are bounded individually; the domain also bounds the combined inference text to 5000. */
public record ReviewRequest(
        @Positive Long jobPostingId,
        @NotBlank @Size(max = 150) String department,
        @NotBlank @Pattern(regexp = "CURRENT|FORMER") String employmentStatus,
        @NotNull @Min(1) @Max(600) Integer tenureMonths,
        @Min(1) @Max(5) int rating,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 1500) String pros,
        @NotBlank @Size(max = 1500) String cons,
        @NotBlank @Size(max = 5000) String body,
        @NotBlank @Size(max = 2000) String managementMessage) {
    /** Compatibility constructor for existing service tests and older internal callers. */
    public ReviewRequest(Long jobPostingId,int rating,String title,String pros,String cons,String body){
        this(jobPostingId,"소속 미입력","CURRENT",1,rating,title,pros,cons,body,"더 나은 근무 환경을 위한 지속적인 소통을 바랍니다.");
    }
}

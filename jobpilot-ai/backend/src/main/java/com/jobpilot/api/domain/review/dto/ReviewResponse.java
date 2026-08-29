package com.jobpilot.api.domain.review.dto;

import com.jobpilot.api.domain.review.entity.CompanyReview;
import java.time.LocalDateTime;
import java.util.Objects;

/** 작성자의 member ID/email은 공개 리뷰나 기업 분석 응답에 포함하지 않는다. */
public record ReviewResponse(Long id, Long companyId, Long jobPostingId, String displayAuthor,
                             String sourceType, int rating, String title, String pros, String cons,
                             String body, String analysisState, boolean mine,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ReviewResponse from(CompanyReview r, Long viewerId) {
        return new ReviewResponse(r.getId(), r.getCompanyId(), r.getJobPostingId(), r.getDisplayAuthor(),
                r.getSourceType(), r.getRating(), r.getTitle(), r.getPros(), r.getCons(), r.getBody(),
                r.getAnalysisState(), viewerId != null && Objects.equals(viewerId, r.getAuthorMemberId()),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}

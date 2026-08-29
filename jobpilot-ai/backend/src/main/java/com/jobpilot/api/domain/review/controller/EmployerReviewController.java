package com.jobpilot.api.domain.review.controller;

import com.jobpilot.api.domain.review.dto.ReviewResponse;
import com.jobpilot.api.domain.review.service.CompanyReviewService;
import com.jobpilot.api.global.security.AuthenticatedEmployer;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 기업회원에게는 조회만 제공한다. 기업/개인 계정 ID가 같아도 별개 principal이다. */
@RestController
@RequestMapping("/api/v1/employer/job-postings/{postingId}/reviews")
public class EmployerReviewController {
    private final CompanyReviewService service;
    private final com.jobpilot.api.domain.review.service.ReviewOperationsService operations;
    public EmployerReviewController(CompanyReviewService service,com.jobpilot.api.domain.review.service.ReviewOperationsService operations) { this.service = service;this.operations=operations; }
    @GetMapping("/summary") public java.util.Map<String,Object> summary(@PathVariable Long postingId,Authentication auth){return operations.employerSummary(AuthenticatedEmployer.id(auth),postingId);}
    @GetMapping public Page<ReviewResponse> list(@PathVariable Long postingId, Authentication auth,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.employerPostingReviews(AuthenticatedEmployer.id(auth), postingId, page, size);
    }
}

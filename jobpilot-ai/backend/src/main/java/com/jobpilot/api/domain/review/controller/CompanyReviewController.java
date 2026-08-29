package com.jobpilot.api.domain.review.controller;

import com.jobpilot.api.domain.review.dto.*;
import com.jobpilot.api.domain.review.repository.ReviewCompanyCatalog;
import com.jobpilot.api.domain.review.service.CompanyReviewService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/review-companies")
public class CompanyReviewController {
    private final CompanyReviewService service;
    private final com.jobpilot.api.domain.review.service.ReviewRankingService rankings;
    private final com.jobpilot.api.domain.review.service.ReviewOperationsService operations;
    public CompanyReviewController(CompanyReviewService service, com.jobpilot.api.domain.review.service.ReviewRankingService rankings,com.jobpilot.api.domain.review.service.ReviewOperationsService operations) { this.service = service; this.rankings=rankings;this.operations=operations; }

    @GetMapping("/rankings/companies") public java.util.List<com.jobpilot.api.domain.review.service.ReviewRankingService.Ranking> companyRankings(){return rankings.companies();}
    @GetMapping("/rankings/postings") public java.util.List<com.jobpilot.api.domain.review.service.ReviewRankingService.Ranking> postingRankings(){return rankings.postings();}

    @GetMapping public List<ReviewCompanyCatalog.Company> companies(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.companies(page, size);
    }
    @GetMapping("/{id}") public ReviewCompanyCatalog.Company company(@PathVariable Long id) { return service.company(id); }
    @GetMapping("/reviews/{reviewId}/analysis")
    public java.util.Map<String, Object> analysis(@PathVariable Long reviewId) {
        var result = service.analysis(reviewId);
        return result == null ? java.util.Map.of("available", false)
                : java.util.Map.of("available", true, "analysis", result);
    }
    @GetMapping("/{id}/reviews") public Page<ReviewResponse> list(@PathVariable Long id, Authentication auth,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(id, AuthenticatedMember.id(auth), page, size);
    }
    @PostMapping("/{id}/reviews") public ReviewResponse create(@PathVariable Long id, Authentication auth,
            @Valid @RequestBody ReviewRequest request) { return service.create(id, AuthenticatedMember.id(auth), request); }
    @PutMapping("/reviews/{reviewId}") public ReviewResponse update(@PathVariable Long reviewId, Authentication auth,
            @Valid @RequestBody ReviewRequest request) { return service.update(reviewId, AuthenticatedMember.id(auth), request); }
    @DeleteMapping("/reviews/{reviewId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long reviewId, Authentication auth) { service.delete(reviewId, AuthenticatedMember.id(auth)); }
    @PostMapping("/reviews/{reviewId}/like") public java.util.Map<String,Object> like(@PathVariable Long reviewId,Authentication auth){return operations.like(reviewId,AuthenticatedMember.id(auth));}
    public record Report(String reason){} @PostMapping("/reviews/{reviewId}/report") public void report(@PathVariable Long reviewId,@RequestBody Report input,Authentication auth){operations.report(reviewId,AuthenticatedMember.id(auth),input.reason());}
}

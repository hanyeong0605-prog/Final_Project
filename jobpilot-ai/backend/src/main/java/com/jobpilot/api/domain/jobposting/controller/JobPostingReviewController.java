package com.jobpilot.api.domain.jobposting.controller;

import com.jobpilot.api.domain.jobposting.dto.JobPostingReviewRequest;
import com.jobpilot.api.domain.jobposting.dto.JobPostingReviewResponse;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.entity.JobPostingReview;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.jobposting.repository.JobPostingReviewRepository;
import com.jobpilot.api.domain.resume.entity.ResumeEntryType;
import com.jobpilot.api.domain.resume.repository.ResumeEntryRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import com.jobpilot.api.global.security.AuthenticatedMember;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/job-postings/{jobPostingId}/reviews")
public class JobPostingReviewController {
    private final JobPostingReviewRepository reviews;
    private final JobPostingRepository postings;
    private final ResumeEntryRepository resumeEntries;
    public JobPostingReviewController(JobPostingReviewRepository reviews, JobPostingRepository postings, ResumeEntryRepository resumeEntries) {
        this.reviews = reviews; this.postings = postings; this.resumeEntries = resumeEntries;
    }
    @GetMapping
    public List<JobPostingReviewResponse> list(@PathVariable Long jobPostingId, Authentication authentication) {
        Long memberId = AuthenticatedMember.id(authentication);
        return reviews.findByJobPostingIdOrderByCreatedAtDesc(jobPostingId).stream().map(item -> response(item, memberId)).toList();
    }
    @PostMapping
    @Transactional
    public JobPostingReviewResponse save(@PathVariable Long jobPostingId, @RequestBody JobPostingReviewRequest request, Authentication authentication) {
        if (request.rating() < 1 || request.rating() > 5) throw new IllegalArgumentException("별점은 1점부터 5점까지 입력해 주세요.");
        String content = request.content() == null ? "" : request.content().trim();
        if (content.length() < 10 || content.length() > 2000) throw new IllegalArgumentException("리뷰는 10자 이상 2,000자 이하로 입력해 주세요.");
        Long memberId = AuthenticatedMember.id(authentication);
        JobPosting posting = postings.findById(jobPostingId).orElseThrow(() -> new ResourceNotFoundException("채용공고를 찾을 수 없습니다."));
        boolean verified = hasMatchingCareer(memberId, posting.getCompanyName());
        JobPostingReview review = reviews.findByJobPostingIdAndMemberId(jobPostingId, memberId)
                .map(existing -> { existing.update(request.rating(), content, verified); return existing; })
                .orElseGet(() -> new JobPostingReview(jobPostingId, memberId, request.rating(), content, verified));
        return response(reviews.save(review), memberId);
    }
    private boolean hasMatchingCareer(Long memberId, String companyName) {
        String company = normalize(companyName);
        if (company.isBlank()) return false;
        return resumeEntries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId).stream()
                .filter(entry -> entry.getEntryType() == ResumeEntryType.CAREER)
                .anyMatch(entry -> normalize(entry.getContent().path("company").asText()).equals(company));
    }
    private String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^가-힣a-z0-9]", ""); }
    private JobPostingReviewResponse response(JobPostingReview item, Long memberId) { return new JobPostingReviewResponse(item.getId(), item.getRating(), item.getContent(), item.isEmploymentVerified(), item.getMemberId().equals(memberId), item.getCreatedAt()); }
}

package com.jobpilot.api.domain.review.service;

import com.jobpilot.api.domain.review.dto.*;
import com.jobpilot.api.domain.review.entity.CompanyReview;
import com.jobpilot.api.domain.review.repository.*;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.domain.employer.service.EmployerAccessService;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CompanyReviewService {
    private final CompanyReviewRepository reviews;
    private final ReviewCompanyCatalog companies;
    private final MemberRepository members;
    private final EmployerAccessService employers;
    private final JobPostingRepository postings;
    private final ReviewAnalysisStore analyses;

    public CompanyReviewService(CompanyReviewRepository reviews, ReviewCompanyCatalog companies,
                                MemberRepository members, EmployerAccessService employers,
                                JobPostingRepository postings, ReviewAnalysisStore analyses) {
        this.reviews = reviews; this.companies = companies; this.members = members;
        this.employers = employers; this.postings = postings;
        this.analyses = analyses;
    }

    public List<ReviewCompanyCatalog.Company> companies(int page, int size) {
        var paging = page(page, size);
        return companies.list(paging.getPageSize(), Math.toIntExact(paging.getOffset()));
    }

    public ReviewCompanyCatalog.Company company(Long id) {
        return companies.find(id).orElseThrow(() -> new ResourceNotFoundException("리뷰 회사를 찾을 수 없습니다."));
    }

    public Page<ReviewResponse> list(Long companyId, Long viewerId, int page, int size) {
        company(companyId);
        return reviews.findByCompanyIdAndVisibility(companyId, "PUBLIC", page(page, size))
                .map(r -> ReviewResponse.from(r, viewerId));
    }

    public com.jobpilot.api.domain.sentiment.client.SentimentAiClient.Analysis analysis(Long reviewId) {
        var review = find(reviewId);
        if (!"PUBLIC".equals(review.getVisibility())) throw new ResourceNotFoundException("리뷰를 찾을 수 없습니다.");
        // No stale result after edits; null explicitly means pending/failed/unavailable, not NEUTRAL.
        return analyses.latest(reviewId).orElse(null);
    }

    @Transactional
    public ReviewResponse create(Long companyId, Long memberId, ReviewRequest request) {
        var company = company(companyId);
        if (!"FICTIONAL_DEMO".equals(company.sourceType()) || !company.reviewsEnabled())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "시연용 가상기업에만 리뷰를 작성할 수 있습니다.");
        if (request.jobPostingId() != null && !companies.acceptsPosting(companyId, request.jobPostingId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 회사의 가상공고가 아닙니다.");
        var member = members.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다."));
        if (reviews.existsByCompanyIdAndAuthorMemberIdAndVisibilityNot(companyId, memberId, "DELETED"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "회사별 리뷰는 하나만 작성할 수 있습니다.");
        var review = CompanyReview.byMember(companyId, request.jobPostingId(), memberId, member.getNickname(),
                request.department(),request.employmentStatus(),request.tenureMonths(),request.rating(), request.title(),
                request.pros(), request.cons(), request.body(),request.managementMessage());
        // No network inside this transaction. PENDING is persisted even when AI is unavailable.
        return ReviewResponse.from(reviews.save(review), memberId);
    }

    @Transactional
    public ReviewResponse update(Long id, Long memberId, ReviewRequest request) {
        var r = find(id);
        // Moving an existing review to another posting would change ranking/ownership history.
        if (!Objects.equals(r.getJobPostingId(), request.jobPostingId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "리뷰의 공고 연결은 변경할 수 없습니다.");
        r.edit(memberId,request.department(),request.employmentStatus(),request.tenureMonths(),request.rating(),request.title(),request.pros(),request.cons(),request.body(),request.managementMessage());
        return ReviewResponse.from(r, memberId);
    }

    @Transactional
    public void delete(Long id, Long memberId) { find(id).deleteByMember(memberId); }

    public Page<ReviewResponse> employerPostingReviews(Long employerId, Long postingId, int page, int size) {
        employers.requireApproved(employerId);
        postings.findByIdAndEmployerAccountId(postingId, employerId)
                .orElseThrow(() -> new ResourceNotFoundException("내 공고를 찾을 수 없습니다."));
        return reviews.findByJobPostingIdAndVisibility(postingId, "PUBLIC", page(page, size))
                .map(r -> ReviewResponse.from(r, null));
    }

    private CompanyReview find(Long id) {
        return reviews.findById(id).orElseThrow(() -> new ResourceNotFoundException("리뷰를 찾을 수 없습니다."));
    }

    private PageRequest page(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 범위가 올바르지 않습니다.");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }
}

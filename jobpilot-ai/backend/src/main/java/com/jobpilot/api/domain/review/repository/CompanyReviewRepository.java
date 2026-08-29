package com.jobpilot.api.domain.review.repository;

import com.jobpilot.api.domain.review.entity.CompanyReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyReviewRepository extends JpaRepository<CompanyReview, Long> {
    Page<CompanyReview> findByCompanyIdAndVisibility(Long companyId, String visibility, Pageable pageable);
    Page<CompanyReview> findByJobPostingIdAndVisibility(Long postingId, String visibility, Pageable pageable);
    boolean existsByCompanyIdAndAuthorMemberIdAndVisibilityNot(Long companyId, Long memberId, String visibility);
}

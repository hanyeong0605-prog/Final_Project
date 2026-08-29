package com.jobpilot.api.domain.review.service;

import com.jobpilot.api.domain.review.dto.*;
import com.jobpilot.api.domain.review.entity.CompanyReview;
import com.jobpilot.api.domain.review.repository.*;
import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.domain.employer.service.EmployerAccessService;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyReviewServiceTest {
    private final CompanyReviewRepository reviews = mock(CompanyReviewRepository.class);
    private final ReviewCompanyCatalog companies = mock(ReviewCompanyCatalog.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final EmployerAccessService employers = mock(EmployerAccessService.class);
    private final JobPostingRepository postings = mock(JobPostingRepository.class);
    private final ReviewAnalysisStore analyses = mock(ReviewAnalysisStore.class);
    private CompanyReviewService service;
    private final ReviewRequest request = new ReviewRequest(10L, 4, "제목", "장점", "단점", "후기");

    @BeforeEach void setup() {
        service = new CompanyReviewService(reviews, companies, members, employers, postings, analyses);
    }
    private void fictionalCompany() {
        when(companies.find(1L)).thenReturn(Optional.of(new ReviewCompanyCatalog.Company(
                1L, "시연 (가상기업)", "설명", "IT", "서울", "FICTIONAL_DEMO", true)));
    }

    @Test void rejectsRealCompanyBeforeSaving() {
        when(companies.find(1L)).thenReturn(Optional.of(new ReviewCompanyCatalog.Company(
                1L, "실제 회사", "설명", "IT", "서울", "CRAWLED", true)));
        assertThatThrownBy(() -> service.create(1L, 100L, request)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(reviews, members);
    }
    @Test void rejectsPostingFromAnotherCompany() {
        fictionalCompany();
        when(companies.acceptsPosting(1L, 10L)).thenReturn(false);
        assertThatThrownBy(() -> service.create(1L, 100L, request)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(reviews, members);
    }
    @Test void savesPendingReviewWithoutAiDependency() {
        fictionalCompany();
        when(companies.acceptsPosting(1L, 10L)).thenReturn(true);
        when(members.findById(100L)).thenReturn(Optional.of(new Member("user", "test@example.invalid", "hash", "리뷰어")));
        when(reviews.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.create(1L, 100L, request);
        assertThat(response.analysisState()).isEqualTo("PENDING");
        assertThat(response.mine()).isTrue();
        assertThat(response.sourceType()).isEqualTo("USER");
    }
    @Test void hiddenReviewStillPreventsDuplicate() {
        fictionalCompany();
        when(companies.acceptsPosting(1L, 10L)).thenReturn(true);
        when(members.findById(100L)).thenReturn(Optional.of(new Member("u", "t@example.invalid", "h", "리뷰어")));
        when(reviews.existsByCompanyIdAndAuthorMemberIdAndVisibilityNot(1L, 100L, "DELETED")).thenReturn(true);
        assertThatThrownBy(() -> service.create(1L, 100L, request)).isInstanceOf(ResponseStatusException.class);
        verify(reviews, never()).save(any());
    }
    @Test void employerMustOwnPostingBeforeAnyReviewQuery() {
        when(postings.findByIdAndEmployerAccountId(10L, 200L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.employerPostingReviews(200L, 10L, 0, 20)).isInstanceOf(ResourceNotFoundException.class);
        verify(employers).requireApproved(200L);
        verifyNoInteractions(reviews);
    }
    @Test void cannotEditOrDeleteAnotherMembersReview() {
        var r = CompanyReview.byMember(1L, 10L, 100L, "리뷰어", 4, "제목", "장점", "단점", "후기");
        when(reviews.findById(5L)).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.update(5L, 101L, request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(5L, 101L)).isInstanceOf(AccessDeniedException.class);
        assertThat(r.getVisibility()).isEqualTo("PUBLIC");
    }
    @Test void rejectsExcessivePagination() {
        assertThatThrownBy(() -> service.companies(Integer.MAX_VALUE, 100)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(companies);
    }
}

package com.jobpilot.api.domain.employer.service;

import com.jobpilot.api.domain.employer.dto.EmployerJobPostingRequest;
import com.jobpilot.api.domain.employer.dto.EmployerJobPostingResponse;
import com.jobpilot.api.domain.employer.entity.EmployerAccount;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * 2026-08-19: 승인된 기업회원이 직접 채용공고를 등록/수정/숨김 처리한다 - 크롤링
 * 공고와 같은 job_postings 테이블을 쓰되(JobPosting.createByEmployer 참고),
 * EmployerAccessService.requireApproved()로 APPROVED 상태인 기업만 통과시킨다.
 */
@Service
@Transactional
public class EmployerJobPostingService {
    private final JobPostingRepository postings;
    private final EmployerAccessService employerAccess;
    private final String publicBaseUrl;

    public EmployerJobPostingService(JobPostingRepository postings, EmployerAccessService employerAccess,
                                      @Value("${app.public-base-url:https://job-a-dream.site}") String publicBaseUrl) {
        this.postings = postings;
        this.employerAccess = employerAccess;
        this.publicBaseUrl = publicBaseUrl;
    }

    public Page<EmployerJobPostingResponse> myPostings(Long employerId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id"));
        return postings.findByEmployerAccountIdOrderByIdDesc(employerId, pageable).map(EmployerJobPostingResponse::from);
    }

    public EmployerJobPostingResponse create(Long employerId, EmployerJobPostingRequest request) {
        EmployerAccount employer = employerAccess.requireApproved(employerId);
        JobPosting posting = JobPosting.createByEmployer(
                employerId, request.title().trim(), employer.getCompanyName(), blankToNull(request.companyUrl()),
                request.description(), blankToNull(request.location()), blankToNull(request.employmentType()),
                blankToNull(request.experienceType()), blankToNull(request.salary()), request.deadlineAt(),
                request.rollingDeadline());
        JobPosting saved = postings.save(posting);
        // 원문 링크가 없는 자체 등록 공고라, 저장 후 생성된 id로 우리 사이트 상세 페이지
        // URL을 채워 넣는다("공고 원문 보기" 버튼이 깨지지 않게).
        saved.assignInternalSourceUrl(publicBaseUrl + "/job-postings/" + saved.getId());
        return EmployerJobPostingResponse.from(postings.save(saved));
    }

    public EmployerJobPostingResponse update(Long employerId, Long jobPostingId, EmployerJobPostingRequest request) {
        EmployerAccount employer = employerAccess.requireApproved(employerId);
        JobPosting posting = findOwned(employerId, jobPostingId);
        posting.updateByEmployer(request.title().trim(), employer.getCompanyName(), blankToNull(request.companyUrl()),
                request.description(), blankToNull(request.location()), blankToNull(request.employmentType()),
                blankToNull(request.experienceType()), blankToNull(request.salary()), request.deadlineAt(),
                request.rollingDeadline());
        return EmployerJobPostingResponse.from(postings.save(posting));
    }

    public EmployerJobPostingResponse hide(Long employerId, Long jobPostingId) {
        employerAccess.requireApproved(employerId);
        JobPosting posting = findOwned(employerId, jobPostingId);
        posting.changeStatus("HIDDEN");
        return EmployerJobPostingResponse.from(postings.save(posting));
    }

    private JobPosting findOwned(Long employerId, Long jobPostingId) {
        return postings.findByIdAndEmployerAccountId(jobPostingId, employerId)
                .orElseThrow(() -> new ResourceNotFoundException("내가 등록한 채용공고를 찾을 수 없습니다."));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

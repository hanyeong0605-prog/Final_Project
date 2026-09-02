package com.jobpilot.api.domain.employer.service;

import com.jobpilot.api.domain.employer.dto.EmployerJobPostingRequest;
import com.jobpilot.api.domain.employer.dto.EmployerJobPostingResponse;
import com.jobpilot.api.domain.employer.entity.EmployerAccount;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.entity.JobRequirement;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.jobposting.repository.JobRequirementRepository;
import com.jobpilot.api.domain.matching.service.JobMatchGenerationService;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Arrays;

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
    private final JobRequirementRepository requirements;
    private final JobMatchGenerationService matches;
    private final JdbcTemplate jdbc;
    private final String publicBaseUrl;

    public EmployerJobPostingService(JobPostingRepository postings, EmployerAccessService employerAccess,
                                      JobRequirementRepository requirements, JobMatchGenerationService matches, JdbcTemplate jdbc,
                                      @Value("${app.public-base-url:https://job-a-dream.site}") String publicBaseUrl) {
        this.postings = postings;
        this.employerAccess = employerAccess;
        this.requirements = requirements;
        this.matches = matches;
        this.jdbc = jdbc;
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
                request.rollingDeadline(), blankToNull(request.qualifications()), blankToNull(request.preferredQualifications()), blankToNull(request.imageUrl()));
        JobPosting saved = postings.save(posting);
        // 원문 링크가 없는 자체 등록 공고라, 저장 후 생성된 id로 우리 사이트 상세 페이지
        // URL을 채워 넣는다("공고 원문 보기" 버튼이 깨지지 않게).
        saved.assignInternalSourceUrl(publicBaseUrl + "/job-postings/" + saved.getId());
        saved = postings.save(saved);
        replaceRequirements(saved, request);
        matches.regenerateForPosting(saved.getId());
        return EmployerJobPostingResponse.from(saved);
    }

    public EmployerJobPostingResponse update(Long employerId, Long jobPostingId, EmployerJobPostingRequest request) {
        EmployerAccount employer = employerAccess.requireApproved(employerId);
        JobPosting posting = findOwned(employerId, jobPostingId);
        posting.updateByEmployer(request.title().trim(), employer.getCompanyName(), blankToNull(request.companyUrl()),
                request.description(), blankToNull(request.location()), blankToNull(request.employmentType()),
                blankToNull(request.experienceType()), blankToNull(request.salary()), request.deadlineAt(),
                request.rollingDeadline(), blankToNull(request.qualifications()), blankToNull(request.preferredQualifications()), blankToNull(request.imageUrl()));
        JobPosting saved = postings.save(posting);
        replaceRequirements(saved, request);
        matches.regenerateForPosting(saved.getId());
        return EmployerJobPostingResponse.from(saved);
    }

    public void delete(Long employerId, Long jobPostingId) {
        employerAccess.requireApproved(employerId);
        JobPosting posting = findOwned(employerId, jobPostingId);
        Long id = posting.getId();
        jdbc.update("delete from job_match_evidences where job_match_id in (select id from job_matches where job_posting_id=?)", id);
        jdbc.update("delete from job_matches where job_posting_id=?", id);
        jdbc.update("delete from job_skills where job_posting_id=?", id);
        jdbc.update("delete from job_requirements where job_posting_id=?", id);
        jdbc.update("delete from member_job_events where job_posting_id=?", id);
        jdbc.update("delete from employer_notifications where job_posting_id=?", id);
        jdbc.update("delete from job_posting_locations where job_posting_id=?", id);
        jdbc.update("delete from user_interests where target_type='JOB_POSTING' and target_id=?", id);
        postings.delete(posting);
    }

    private JobPosting findOwned(Long employerId, Long jobPostingId) {
        return postings.findByIdAndEmployerAccountId(jobPostingId, employerId)
                .orElseThrow(() -> new ResourceNotFoundException("내가 등록한 채용공고를 찾을 수 없습니다."));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void replaceRequirements(JobPosting posting, EmployerJobPostingRequest request) {
        requirements.deleteByJobPostingId(posting.getId());
        saveRequirementLines(posting.getId(), request.qualifications(), "REQUIRED");
        saveRequirementLines(posting.getId(), request.preferredQualifications(), "PREFERRED");
        if (!requirements.existsByJobPostingId(posting.getId()) && request.description() != null && !request.description().isBlank()) {
            requirements.save(new JobRequirement(posting.getId(), "SKILL", request.description().trim(), request.description().trim(), "REQUIRED", "EMPLOYER_FORM", "VERIFIED"));
        }
    }

    private void saveRequirementLines(Long postingId, String text, String importance) {
        if (text == null || text.isBlank()) return;
        Arrays.stream(text.split("\\r?\\n|[•·]")).map(String::trim).filter(line -> !line.isBlank()).limit(30)
                .forEach(line -> requirements.save(new JobRequirement(postingId, requirementType(line), line, line, importance, "EMPLOYER_FORM", "VERIFIED")));
    }

    private String requirementType(String value) {
        String text = value.toLowerCase();
        if (text.contains("경력") || text.contains("experience")) return "EXPERIENCE";
        if (text.contains("학력") || text.contains("전공") || text.contains("학사") || text.contains("석사")) return "EDUCATION";
        if (text.contains("자격증") || text.contains("certificate")) return "CERTIFICATION";
        return "SKILL";
    }
}

package com.jobpilot.api.domain.jobposting.controller;

import com.jobpilot.api.domain.jobposting.dto.JobPostingListResponse;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-postings")
public class JobPostingController {
    private final JobPostingRepository repository;

    public JobPostingController(JobPostingRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<JobPostingListResponse> findAll(@RequestParam(required = false) String query) {
        String keyword = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return repository.findByStatusOrderByPublishedAtDesc("ACTIVE").stream()
                .filter(posting -> keyword.isEmpty() || contains(posting, keyword))
                .map(this::toResponse)
                .toList();
    }

    private boolean contains(JobPosting posting, String keyword) {
        return value(posting.getTitle()).contains(keyword)
                || value(posting.getCompanyName()).contains(keyword)
                || value(posting.getLocation()).contains(keyword)
                || value(posting.getJobName()).contains(keyword)
                || value(posting.getKeywords()).contains(keyword);
    }

    private String value(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private JobPostingListResponse toResponse(JobPosting posting) {
        return new JobPostingListResponse(
                posting.getId(), posting.getExternalJobId(), posting.getCompanyName(), posting.getTitle(),
                posting.getSourceUrl(), posting.getLocation(), posting.getEmploymentType(), posting.getExperienceType(),
                posting.getJobName(), posting.getSalary(), posting.getKeywords(), posting.getPublishedAt(),
                posting.getDeadlineAt(), posting.isRollingDeadline(), posting.getStatus()
        );
    }
}

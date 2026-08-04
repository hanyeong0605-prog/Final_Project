package com.jobpilot.api.domain.jobposting.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.api.domain.jobposting.dto.JobPostingDetailResponse;
import com.jobpilot.api.domain.jobposting.dto.JobPostingListResponse;
import com.jobpilot.api.domain.jobposting.dto.JobPostingLocationResponse;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingLocationRepository;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-postings")
public class JobPostingController {
    private final JobPostingRepository repository;
    private final JobPostingLocationRepository locationRepository;

    public JobPostingController(
            JobPostingRepository repository,
            JobPostingLocationRepository locationRepository) {
        this.repository = repository;
        this.locationRepository = locationRepository;
    }

    @GetMapping
    public List<JobPostingListResponse> findAll(@RequestParam(required = false) String query) {
        String keyword = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return repository.findByStatusOrderByPublishedAtDesc("ACTIVE").stream()
                .filter(posting -> keyword.isEmpty() || contains(posting, keyword))
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public JobPostingDetailResponse findById(@PathVariable Long id) {
        JobPosting posting = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("채용공고를 찾을 수 없습니다."));
        return toDetailResponse(posting);
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

    private JobPostingDetailResponse toDetailResponse(JobPosting posting) {
        return new JobPostingDetailResponse(
                posting.getId(), posting.getExternalJobId(), posting.getSourceProvider(),
                posting.getCompanyName(), posting.getCompanyUrl(), posting.getCompanyLogoUrl(),
                posting.getTitle(), posting.getDescription(), posting.getSourceUrl(), posting.getLocation(),
                posting.getEmploymentType(), posting.getExperienceType(), posting.getEntryLevel(),
                posting.getIndustryName(), posting.getJobMidName(), posting.getJobName(), posting.getSalary(),
                posting.getKeywords(), posting.getPublishedAt(), posting.getDeadlineAt(), posting.isRollingDeadline(),
                posting.getStatus(), locations(posting.getId()),
                imageUrls(posting.getRawPayload())
        );
    }

    private List<JobPostingLocationResponse> locations(Long jobPostingId) {
        return locationRepository.findByJobPostingIdOrderByPrimaryLocationDescIdAsc(jobPostingId).stream()
                .map(location -> new JobPostingLocationResponse(
                        location.getLocationText(), location.getSido(), location.getSigungu(),
                        location.getDetailedAddress(), location.getLatitude(), location.getLongitude(),
                        location.isPrimaryLocation()))
                .toList();
    }

    private List<String> imageUrls(JsonNode rawPayload) {
        if (rawPayload == null) return List.of();
        JsonNode urls = rawPayload.path("images").path("job_thumbnail_urls");
        if (!urls.isArray()) return List.of();

        List<String> result = new ArrayList<>();
        for (JsonNode url : urls) {
            if (url.isTextual() && (url.asText().startsWith("https://") || url.asText().startsWith("http://"))) {
                result.add(url.asText());
            }
        }
        return result;
    }
}

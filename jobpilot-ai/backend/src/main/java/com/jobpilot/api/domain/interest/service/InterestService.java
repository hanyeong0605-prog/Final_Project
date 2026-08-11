package com.jobpilot.api.domain.interest.service;

import com.jobpilot.api.domain.interest.dto.InterestToggleRequest;
import com.jobpilot.api.domain.interest.dto.InterestToggleResponse;
import com.jobpilot.api.domain.interest.entity.UserInterest;
import com.jobpilot.api.domain.interest.repository.UserInterestRepository;
import com.jobpilot.api.domain.jobposting.dto.JobPostingListResponse;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.jobposting.repository.JobPostingRepository;
import com.jobpilot.api.domain.planner.entity.PlannerEvent;
import com.jobpilot.api.domain.planner.repository.PlannerEventRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class InterestService {
    private static final String JOB = "JOB_POSTING";
    private final UserInterestRepository interests;
    private final JobPostingRepository jobs;
    private final PlannerEventRepository events;

    public InterestService(UserInterestRepository interests, JobPostingRepository jobs, PlannerEventRepository events) {
        this.interests = interests; this.jobs = jobs; this.events = events;
    }

    public List<Long> ids(Long memberId, String type) {
        return interests.findByMemberIdAndTargetTypeOrderByCreatedAtDesc(memberId, type).stream().map(UserInterest::getTargetId).toList();
    }

    public List<JobPostingListResponse> bookmarkedJobs(Long memberId) {
        return interests.findByMemberIdAndTargetTypeOrderByCreatedAtDesc(memberId, JOB).stream()
                .map(value -> jobs.findById(value.getTargetId()).orElse(null)).filter(java.util.Objects::nonNull)
                .map(this::jobResponse).toList();
    }

    public InterestToggleResponse toggle(Long memberId, InterestToggleRequest request) {
        var existing = interests.findByMemberIdAndTargetTypeAndTargetId(memberId, request.targetType(), request.targetId());
        if (request.interested() && existing.isEmpty()) {
            interests.save(new UserInterest(memberId, request.targetType(), request.targetId()));
            if (JOB.equals(request.targetType())) addJobEvent(memberId, request.targetId());
        } else if (!request.interested()) {
            existing.ifPresent(interests::delete);
            if (JOB.equals(request.targetType())) events.findByMemberIdAndSourceTypeAndSourceIdAndEventType(
                    memberId, JOB, request.targetId(), "APPLICATION_PERIOD").ifPresent(events::delete);
        }
        return new InterestToggleResponse(request.targetId(), request.interested());
    }

    private void addJobEvent(Long memberId, Long jobId) {
        JobPosting job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("채용공고를 찾을 수 없습니다."));
        if (job.getDeadlineAt() == null && job.getPublishedAt() == null) return;
        LocalDateTime start = job.getPublishedAt() != null ? job.getPublishedAt() : job.getDeadlineAt();
        events.findByMemberIdAndSourceTypeAndSourceIdAndEventType(memberId, JOB, jobId, "APPLICATION_PERIOD")
                .orElseGet(() -> events.save(PlannerEvent.fromJobPosting(memberId, jobId,
                        (job.getCompanyName() == null ? "" : job.getCompanyName() + " · ") + job.getTitle(),
                        start, job.getDeadlineAt())));
    }

    private JobPostingListResponse jobResponse(JobPosting posting) {
        return new JobPostingListResponse(posting.getId(), posting.getExternalJobId(), posting.getCompanyName(), posting.getCompanyLogoUrl(), thumbnailUrl(posting), posting.getTitle(),
                posting.getSourceUrl(), posting.getLocation(), posting.getEmploymentType(), posting.getExperienceType(),
                posting.getJobName(), posting.getSalary(), posting.getKeywords(), posting.getPublishedAt(), posting.getDeadlineAt(),
                posting.isRollingDeadline(), posting.getStatus(), posting.getViewCount(), 0L);
    }

    private String thumbnailUrl(JobPosting posting) {
        var rawPayload = posting.getRawPayload();
        var url = rawPayload == null ? null : rawPayload.path("images").path("job_thumbnail_urls").path(0);
        return url != null && url.isTextual() ? url.asText() : posting.getCompanyLogoUrl();
    }
}

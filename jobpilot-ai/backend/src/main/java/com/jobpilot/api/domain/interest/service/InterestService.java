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
import com.jobpilot.api.domain.opportunity.repository.OpportunityRepository;
import com.jobpilot.api.domain.matching.service.MemberJobEventService;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import com.jobpilot.api.domain.employer.entity.EmployerNotification;
import com.jobpilot.api.domain.employer.repository.EmployerNotificationRepository;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class InterestService {
    private static final String JOB = "JOB_POSTING";
    private final UserInterestRepository interests;
    private final JobPostingRepository jobs;
    private final PlannerEventRepository events;
    private final MemberJobEventService memberJobEvents;
    private final OpportunityRepository opportunities;
    private final EmployerNotificationRepository employerNotifications;
    private final MemberRepository members;

    public InterestService(UserInterestRepository interests, JobPostingRepository jobs, PlannerEventRepository events,
                           MemberJobEventService memberJobEvents, OpportunityRepository opportunities,
                           EmployerNotificationRepository employerNotifications, MemberRepository members) {
        this.interests = interests; this.jobs = jobs; this.events = events; this.memberJobEvents = memberJobEvents; this.opportunities=opportunities;
        this.employerNotifications = employerNotifications; this.members = members;
    }

    public List<Long> ids(Long memberId, String type) {
        return interests.findByMemberIdAndTargetTypeOrderByCreatedAtDesc(memberId, type).stream().map(UserInterest::getTargetId).toList();
    }

    // 2026-08-26: 원래는 찜한 항목 개수만큼 jobs.findById()를 개별 호출하는 N+1이었다 -
    // 마이페이지가 찜 버튼을 누를 때마다(interestCount 변경) 이 엔드포인트를 다시 부르는데,
    // 찜한 공고가 많을수록 그만큼 느려져서 "찜 버튼 반응이 느리다"는 체감으로 이어졌다.
    // findAllById로 한 번에 배치 조회하고, 원래 찜한 순서(최신순)는 Map에서 다시 매핑해서 유지한다.
    public List<JobPostingListResponse> bookmarkedJobs(Long memberId) {
        List<UserInterest> saved = interests.findByMemberIdAndTargetTypeOrderByCreatedAtDesc(memberId, JOB);
        List<Long> targetIds = saved.stream().map(UserInterest::getTargetId).toList();
        Map<Long, JobPosting> byId = jobs.findAllById(targetIds).stream()
                .collect(Collectors.toMap(JobPosting::getId, Function.identity()));
        return saved.stream()
                .map(value -> byId.get(value.getTargetId()))
                .filter(java.util.Objects::nonNull)
                .map(this::jobResponse).toList();
    }

    public InterestToggleResponse toggle(Long memberId, InterestToggleRequest request) {
        var existing = interests.findByMemberIdAndTargetTypeAndTargetId(memberId, request.targetType(), request.targetId());
        if (request.interested() && existing.isEmpty()) {
            interests.save(new UserInterest(memberId, request.targetType(), request.targetId()));
            if (JOB.equals(request.targetType())) {
                // 2026-08-26: addJobEvent/notifyEmployer가 각자 jobs.findById()를 따로 불러서
                // 공고 찜 POST 한 번에 같은 행을 두 번 조회하고 있었다 - 한 번만 조회해서 넘긴다.
                JobPosting job = jobs.findById(request.targetId())
                        .orElseThrow(() -> new ResourceNotFoundException("채용공고를 찾을 수 없습니다."));
                addJobEvent(memberId, job);
                memberJobEvents.record(memberId, request.targetId(), "BOOKMARK");
                notifyEmployer(memberId, job);
            }
            if ("OPPORTUNITY".equals(request.targetType())) opportunities.findById(request.targetId()).ifPresent(item -> { if (item.getEventStartAt()!=null) events.findByMemberIdAndSourceTypeAndSourceIdAndEventType(memberId,"OPPORTUNITY",item.getId(),"TRAINING_PERIOD").orElseGet(() -> events.save(PlannerEvent.fromOpportunity(memberId,item.getId(),item.getTitle(),item.getEventStartAt(),item.getEventEndAt()))); });
        } else if (!request.interested()) {
            existing.ifPresent(interests::delete);
            if (JOB.equals(request.targetType())) events.findByMemberIdAndSourceTypeAndSourceIdAndEventType(
                        memberId, JOB, request.targetId(), "APPLICATION_PERIOD").ifPresent(events::delete);
            if ("OPPORTUNITY".equals(request.targetType())) events.findByMemberIdAndSourceTypeAndSourceIdAndEventType(memberId,"OPPORTUNITY",request.targetId(),"TRAINING_PERIOD").ifPresent(events::delete);
        }
        return new InterestToggleResponse(request.targetId(), request.interested());
    }

    /** Used by the planner's × button: remove the job bookmark and its automatic schedule together. */
    public void removeJobBookmark(Long memberId, Long jobPostingId) {
        var existing = interests.findByMemberIdAndTargetTypeAndTargetId(memberId, JOB, jobPostingId);
        existing.ifPresent(interests::delete);
        events.deleteByMemberIdAndSourceTypeAndSourceId(memberId, JOB, jobPostingId);
    }

    private void notifyEmployer(Long memberId, JobPosting job) {
        if (job.getEmployerAccountId() == null) return;
        String nickname = members.findById(memberId).map(value -> value.getNickname()).orElse("일반회원");
        employerNotifications.save(new EmployerNotification(job.getEmployerAccountId(), memberId, job.getId(),
                "회원이 내 공고를 찜했습니다.", nickname + "님이 ‘" + job.getTitle() + "’ 공고를 관심 목록에 저장했습니다."));
    }

    private void addJobEvent(Long memberId, JobPosting job) {
        if (job.getDeadlineAt() == null && job.getPublishedAt() == null) return;
        LocalDateTime start = job.getPublishedAt() != null ? job.getPublishedAt() : job.getDeadlineAt();
        events.findByMemberIdAndSourceTypeAndSourceIdAndEventType(memberId, JOB, job.getId(), "APPLICATION_PERIOD")
                .orElseGet(() -> events.save(PlannerEvent.fromJobPosting(memberId, job.getId(),
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
        var url = rawPayload == null ? null : rawPayload.path("imageUrls").path(0);
        if (url == null || !url.isTextual()) {
            url = rawPayload == null ? null : rawPayload.path("images").path("job_thumbnail_urls").path(0);
        }
        return url != null && url.isTextual() ? url.asText() : posting.getCompanyLogoUrl();
    }
}

package com.jobpilot.api.domain.matching.controller;

import com.jobpilot.api.domain.matching.dto.JobMatchDetailResponse;
import com.jobpilot.api.domain.matching.dto.JobMatchSummaryResponse;
import com.jobpilot.api.domain.matching.policy.RecommendationLevel;
import com.jobpilot.api.domain.matching.service.JobMatchService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import com.jobpilot.api.global.security.AuthenticatedMember;

@RestController
@RequestMapping("/api/v1/job-matches")
public class JobMatchController {
    private final JobMatchService jobMatchService;

    public JobMatchController(JobMatchService jobMatchService) {
        this.jobMatchService = jobMatchService;
    }

    /** JWT 적용 전 개발 단계에서는 memberId를 명시적으로 받는다. 인증 도입 후 principal로 교체한다. */
    @GetMapping
    public List<JobMatchSummaryResponse> getMatches(
            Authentication authentication,
            @RequestParam(required = false) RecommendationLevel level
    ) {
        return jobMatchService.findMatches(AuthenticatedMember.id(authentication), level);
    }

    @GetMapping("/{jobPostingId}")
    public JobMatchDetailResponse getMatchDetail(
            @PathVariable Long jobPostingId,
            Authentication authentication
    ) {
        return jobMatchService.findDetail(AuthenticatedMember.id(authentication), jobPostingId);
    }
}

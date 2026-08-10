package com.jobpilot.api.domain.matching.controller;

import com.jobpilot.api.domain.matching.dto.JobMatchDetailResponse;
import com.jobpilot.api.domain.matching.dto.JobMatchSummaryResponse;
import com.jobpilot.api.domain.matching.policy.RecommendationLevel;
import com.jobpilot.api.domain.matching.service.JobMatchService;
import com.jobpilot.api.domain.matching.service.JobMatchGenerationService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final JobMatchGenerationService matchGenerationService;

    public JobMatchController(JobMatchService jobMatchService, JobMatchGenerationService matchGenerationService) {
        this.jobMatchService = jobMatchService;
        this.matchGenerationService = matchGenerationService;
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

    /** 기존 회원도 배포 직후 또는 스펙 수정 후 즉시 매칭 결과를 다시 만들 수 있다. */
    @PostMapping("/recalculate")
    public Map<String, Integer> recalculate(Authentication authentication) {
        return Map.of("generated", matchGenerationService.regenerateForMember(AuthenticatedMember.id(authentication)));
    }
}

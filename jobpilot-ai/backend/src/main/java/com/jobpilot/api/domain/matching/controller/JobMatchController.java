package com.jobpilot.api.domain.matching.controller;

import com.jobpilot.api.domain.matching.dto.JobMatchDetailResponse;
import com.jobpilot.api.domain.matching.dto.JobMatchSummaryResponse;
import com.jobpilot.api.domain.matching.policy.RecommendationLevel;
import com.jobpilot.api.domain.matching.service.JobMatchService;
import com.jobpilot.api.domain.matching.service.JobMatchGenerationService;
import com.jobpilot.api.domain.matching.service.JobMatchLearningClient;
import com.jobpilot.api.domain.matching.service.JobMatchGrowthActionService;
import com.jobpilot.api.domain.matching.dto.GrowthActionResponse;
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
    private final JobMatchLearningClient learningClient;
    private final JobMatchGrowthActionService growthActions;

    public JobMatchController(JobMatchService jobMatchService, JobMatchGenerationService matchGenerationService,
                              JobMatchLearningClient learningClient, JobMatchGrowthActionService growthActions) {
        this.jobMatchService = jobMatchService;
        this.matchGenerationService = matchGenerationService;
        this.learningClient = learningClient;
        this.growthActions = growthActions;
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

    @GetMapping("/{jobPostingId}/growth-actions")
    public List<GrowthActionResponse> growthActions(@PathVariable Long jobPostingId, Authentication authentication) {
        return growthActions.forMatch(AuthenticatedMember.id(authentication), jobPostingId);
    }

    /** 기존 회원도 배포 직후 또는 스펙 수정 후 즉시 매칭 결과를 다시 만들 수 있다. */
    @PostMapping("/recalculate")
    public Map<String, Integer> recalculate(Authentication authentication) {
        return Map.of("generated", matchGenerationService.regenerateForMember(AuthenticatedMember.id(authentication)));
    }

    /** Train from bookmarks when an operator or a future admin action asks for it. */
    @PostMapping("/model/retrain")
    public Map<String, Object> retrainModel(Authentication authentication) {
        AuthenticatedMember.id(authentication);
        return learningClient.retrain();
    }
}

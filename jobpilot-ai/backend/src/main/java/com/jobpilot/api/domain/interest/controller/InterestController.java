package com.jobpilot.api.domain.interest.controller;

import com.jobpilot.api.domain.interest.dto.InterestToggleRequest;
import com.jobpilot.api.domain.interest.dto.InterestToggleResponse;
import com.jobpilot.api.domain.interest.service.InterestService;
import com.jobpilot.api.domain.jobposting.dto.JobPostingListResponse;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/interests")
public class InterestController {
    private final InterestService service;
    public InterestController(InterestService service) { this.service = service; }

    @GetMapping
    public List<Long> findTargetIds(Authentication auth, @RequestParam(defaultValue = "JOB_POSTING") String targetType) {
        return service.ids(AuthenticatedMember.id(auth), targetType);
    }

    @GetMapping("/job-postings")
    public List<JobPostingListResponse> bookmarkedJobs(Authentication auth) {
        return service.bookmarkedJobs(AuthenticatedMember.id(auth));
    }

    @PostMapping
    public InterestToggleResponse toggle(@Valid @RequestBody InterestToggleRequest request, Authentication auth) {
        return service.toggle(AuthenticatedMember.id(auth), request);
    }
}

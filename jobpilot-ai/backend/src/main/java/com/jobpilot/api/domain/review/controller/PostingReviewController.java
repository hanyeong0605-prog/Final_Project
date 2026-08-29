package com.jobpilot.api.domain.review.controller;

import com.jobpilot.api.domain.review.service.PostingReviewOverviewService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/job-postings/{postingId}/reviews")
public class PostingReviewController {
    private final PostingReviewOverviewService service;
    public PostingReviewController(PostingReviewOverviewService service){this.service=service;}
    @GetMapping public PostingReviewOverviewService.Overview overview(@PathVariable long postingId,Authentication auth){
        return service.find(postingId,auth==null?null:AuthenticatedMember.id(auth));
    }
}

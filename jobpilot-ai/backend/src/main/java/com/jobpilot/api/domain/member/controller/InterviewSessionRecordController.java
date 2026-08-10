package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.member.dto.InterviewSessionRecordDetailResponse;
import com.jobpilot.api.domain.member.dto.InterviewSessionRecordRequest;
import com.jobpilot.api.domain.member.dto.InterviewSessionRecordSummaryResponse;
import com.jobpilot.api.domain.member.service.InterviewSessionRecordService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// 2026-08-10: 개인 타임라인 기능(태스크 #66) - SelfIntroductionController와 같은 원칙,
// /api/v1/members/me/** 라 별도 인증 게이팅 코드가 필요 없다. 과거 기록이라 PUT(수정)이
// 없다 - InterviewSessionRecordService docstring 참고.
@RestController
@RequestMapping("/api/v1/members/me/interview-sessions")
public class InterviewSessionRecordController {
    private final InterviewSessionRecordService service;

    public InterviewSessionRecordController(InterviewSessionRecordService service) {
        this.service = service;
    }

    @GetMapping
    public List<InterviewSessionRecordSummaryResponse> list(Authentication auth) {
        return service.list(AuthenticatedMember.id(auth));
    }

    @GetMapping("/{id}")
    public InterviewSessionRecordDetailResponse detail(Authentication auth, @PathVariable Long id) {
        return service.detail(AuthenticatedMember.id(auth), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewSessionRecordDetailResponse create(
            Authentication auth, @Valid @RequestBody InterviewSessionRecordRequest request
    ) {
        return service.create(AuthenticatedMember.id(auth), request);
    }
}

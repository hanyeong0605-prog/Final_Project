package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.member.dto.SelfIntroductionRequest;
import com.jobpilot.api.domain.member.dto.SelfIntroductionResponse;
import com.jobpilot.api.domain.member.service.SelfIntroductionService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// 2026-08-10: /api/v1/members/me/** 경로는 SecurityConfig의 기본 규칙
// (anyRequest().authenticated())으로 이미 로그인 회원만 접근 가능 - 비회원은 이 컨트롤러의
// 어떤 엔드포인트를 호출해도 401을 받는다(별도 게이팅 코드 불필요).
@RestController
@RequestMapping("/api/v1/members/me/self-introductions")
public class SelfIntroductionController {
    private final SelfIntroductionService service;

    public SelfIntroductionController(SelfIntroductionService service) {
        this.service = service;
    }

    @GetMapping
    public List<SelfIntroductionResponse> list(Authentication auth) {
        return service.list(AuthenticatedMember.id(auth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SelfIntroductionResponse create(Authentication auth, @Valid @RequestBody SelfIntroductionRequest request) {
        return service.create(AuthenticatedMember.id(auth), request);
    }

    @PutMapping("/{id}")
    public SelfIntroductionResponse update(
            Authentication auth, @PathVariable Long id, @Valid @RequestBody SelfIntroductionRequest request
    ) {
        return service.update(AuthenticatedMember.id(auth), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long id) {
        service.delete(AuthenticatedMember.id(auth), id);
    }
}

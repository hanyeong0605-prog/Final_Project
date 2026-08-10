package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.member.dto.ProjectRequest;
import com.jobpilot.api.domain.member.dto.ProjectResponse;
import com.jobpilot.api.domain.member.service.ProjectService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// 2026-08-10: /api/v1/members/me/** 는 SecurityConfig 기본 규칙(anyRequest().authenticated())
// 으로 이미 로그인 회원 전용 - 비회원은 401.
@RestController
@RequestMapping("/api/v1/members/me/projects")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProjectResponse> list(Authentication auth) {
        return service.list(AuthenticatedMember.id(auth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(Authentication auth, @Valid @RequestBody ProjectRequest request) {
        return service.create(AuthenticatedMember.id(auth), request);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(Authentication auth, @PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return service.update(AuthenticatedMember.id(auth), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long id) {
        service.delete(AuthenticatedMember.id(auth), id);
    }
}

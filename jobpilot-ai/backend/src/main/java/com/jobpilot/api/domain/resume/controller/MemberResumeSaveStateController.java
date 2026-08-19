package com.jobpilot.api.domain.resume.controller;

import com.jobpilot.api.domain.resume.dto.ResumeSaveStateRequest;
import com.jobpilot.api.domain.resume.dto.ResumeSaveStateResponse;
import com.jobpilot.api.domain.resume.service.MemberResumeSaveStateService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/resume-save-state")
public class MemberResumeSaveStateController {
    private final MemberResumeSaveStateService service;
    public MemberResumeSaveStateController(MemberResumeSaveStateService service) { this.service = service; }
    @GetMapping public ResumeSaveStateResponse get(Authentication auth) { return service.get(AuthenticatedMember.id(auth)); }
    @PutMapping public ResumeSaveStateResponse save(Authentication auth, @Valid @RequestBody ResumeSaveStateRequest request) { return service.save(AuthenticatedMember.id(auth), request.status()); }
}

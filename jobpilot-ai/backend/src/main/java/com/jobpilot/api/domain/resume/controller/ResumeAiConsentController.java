package com.jobpilot.api.domain.resume.controller;

import com.jobpilot.api.domain.resume.dto.ResumeAiConsentRequest;
import com.jobpilot.api.domain.resume.dto.ResumeAiConsentResponse;
import com.jobpilot.api.domain.resume.service.ResumeAiConsentService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/members/me/resume-ai-consent")
public class ResumeAiConsentController {
    private final ResumeAiConsentService service;
    public ResumeAiConsentController(ResumeAiConsentService service) { this.service = service; }
    @GetMapping public ResumeAiConsentResponse get(Authentication auth) { return service.get(AuthenticatedMember.id(auth)); }
    @PutMapping public ResumeAiConsentResponse save(Authentication auth, @RequestBody ResumeAiConsentRequest request) { return service.save(AuthenticatedMember.id(auth), request.agreed()); }
}

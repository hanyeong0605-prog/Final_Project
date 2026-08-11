package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.member.dto.MemberCertificateRequest;
import com.jobpilot.api.domain.member.dto.MemberCertificateResponse;
import com.jobpilot.api.domain.member.service.MemberCertificateService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/certificates")
public class MemberCertificateController {
    private final MemberCertificateService service;

    public MemberCertificateController(MemberCertificateService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemberCertificateResponse> get(Authentication auth) {
        return service.get(AuthenticatedMember.id(auth));
    }

    @PutMapping
    public List<MemberCertificateResponse> replace(Authentication auth,
            @Valid @RequestBody List<@Valid MemberCertificateRequest> request) {
        return service.replace(AuthenticatedMember.id(auth), request);
    }
}

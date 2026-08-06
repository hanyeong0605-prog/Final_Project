package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.member.dto.MemberSkillRequest;
import com.jobpilot.api.domain.member.dto.MemberSkillResponse;
import com.jobpilot.api.domain.member.service.MemberSkillService;
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
@RequestMapping("/api/v1/members/me/skills")
public class MemberSkillController {
    private final MemberSkillService service;

    public MemberSkillController(MemberSkillService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemberSkillResponse> get(Authentication auth) {
        return service.get(AuthenticatedMember.id(auth));
    }

    @PutMapping
    public List<MemberSkillResponse> replace(
            Authentication auth,
            @Valid @RequestBody List<@Valid MemberSkillRequest> request
    ) {
        return service.replace(AuthenticatedMember.id(auth), request);
    }
}

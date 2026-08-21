package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.auth.dto.MemberResponse;
import com.jobpilot.api.domain.member.dto.MemberCareerProfileRequest;
import com.jobpilot.api.domain.member.dto.MemberCareerProfileResponse;
import com.jobpilot.api.domain.member.service.MemberCareerProfileService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members/me/career-profile")
public class MemberCareerProfileController {
    private final MemberCareerProfileService service;
    public MemberCareerProfileController(MemberCareerProfileService service) { this.service = service; }
    @GetMapping
    public ResponseEntity<MemberCareerProfileResponse> get(Authentication auth) {
        MemberCareerProfileResponse value = service.get(AuthenticatedMember.id(auth));
        return value == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(value);
    }
    @PutMapping
    public MemberCareerProfileResponse save(Authentication auth, @Valid @RequestBody MemberCareerProfileRequest request) {
        return service.save(AuthenticatedMember.id(auth), request);
    }
    @DeleteMapping
    public ResponseEntity<Void> reset(Authentication auth) { service.reset(AuthenticatedMember.id(auth)); return ResponseEntity.noContent().build(); }
    @PostMapping("/skip")
    public MemberResponse skip(Authentication auth) { return service.skip(AuthenticatedMember.id(auth)); }
}

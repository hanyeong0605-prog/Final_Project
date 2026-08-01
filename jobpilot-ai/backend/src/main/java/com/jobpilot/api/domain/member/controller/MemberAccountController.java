package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.auth.dto.MemberResponse;
import com.jobpilot.api.domain.member.dto.NicknameUpdateRequest;
import com.jobpilot.api.domain.member.dto.PasswordUpdateRequest;
import com.jobpilot.api.domain.member.dto.WithdrawalRequest;
import com.jobpilot.api.domain.member.service.MemberAccountService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me")
public class MemberAccountController {
    private final MemberAccountService service;

    public MemberAccountController(MemberAccountService service) { this.service = service; }

    @PatchMapping("/nickname")
    public MemberResponse changeNickname(Authentication auth, @Valid @RequestBody NicknameUpdateRequest request) {
        return service.changeNickname(AuthenticatedMember.id(auth), request);
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication auth, @Valid @RequestBody PasswordUpdateRequest request) {
        service.changePassword(AuthenticatedMember.id(auth), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(Authentication auth, @Valid @RequestBody WithdrawalRequest request) {
        service.withdraw(AuthenticatedMember.id(auth), request);
    }
}

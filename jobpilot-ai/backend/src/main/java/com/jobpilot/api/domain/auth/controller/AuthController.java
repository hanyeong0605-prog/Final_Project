package com.jobpilot.api.domain.auth.controller;

import com.jobpilot.api.domain.auth.dto.AuthResponse;
import com.jobpilot.api.domain.auth.dto.LoginRequest;
import com.jobpilot.api.domain.auth.dto.MemberResponse;
import com.jobpilot.api.domain.auth.dto.SignupRequest;
import com.jobpilot.api.domain.auth.service.AuthService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) { return service.signup(request); }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return service.login(request); }

    @GetMapping("/me")
    public MemberResponse me(Authentication authentication) {
        return service.me(AuthenticatedMember.id(authentication));
    }
}

package com.jobpilot.api.domain.employer.controller;

import com.jobpilot.api.domain.employer.dto.EmployerResponse;
import com.jobpilot.api.domain.employer.dto.EmployerSignupRequest;
import com.jobpilot.api.domain.employer.service.EmployerAuthService;
import com.jobpilot.api.global.security.AuthenticatedEmployer;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employer/auth")
public class EmployerAuthController {
    private final EmployerAuthService service;

    public EmployerAuthController(EmployerAuthService service) {
        this.service = service;
    }

    @PostMapping("/signup")
    public EmployerResponse signup(@Valid @RequestBody EmployerSignupRequest request) { return service.signup(request); }

    @GetMapping("/me")
    public EmployerResponse me(Authentication authentication) {
        return service.me(AuthenticatedEmployer.id(authentication));
    }
}

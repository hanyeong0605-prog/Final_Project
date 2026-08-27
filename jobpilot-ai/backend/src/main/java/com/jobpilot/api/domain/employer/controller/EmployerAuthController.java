package com.jobpilot.api.domain.employer.controller;

import com.jobpilot.api.domain.auth.dto.LoginIdAvailabilityResponse;
import com.jobpilot.api.domain.employer.dto.EmployerResponse;
import com.jobpilot.api.domain.employer.dto.EmployerAuthResponse;
import com.jobpilot.api.domain.employer.dto.EmployerLoginRequest;
import com.jobpilot.api.domain.employer.dto.EmployerProfileUpdateRequest;
import com.jobpilot.api.domain.employer.dto.EmployerSignupRequest;
import com.jobpilot.api.domain.employer.service.EmployerAuthService;
import com.jobpilot.api.global.security.AuthenticatedEmployer;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @PostMapping("/login")
    public EmployerAuthResponse login(@Valid @RequestBody EmployerLoginRequest request) { return service.login(request); }

    @GetMapping("/login-id-availability")
    public LoginIdAvailabilityResponse loginIdAvailability(@RequestParam String loginId) {
        return new LoginIdAvailabilityResponse(service.isLoginIdAvailable(loginId));
    }

    @GetMapping("/me")
    public EmployerResponse me(Authentication authentication) {
        return service.me(AuthenticatedEmployer.id(authentication));
    }

    @PutMapping("/me")
    public EmployerResponse updateMe(Authentication authentication,
                                     @Valid @RequestBody EmployerProfileUpdateRequest request) {
        return service.updateProfile(AuthenticatedEmployer.id(authentication), request);
    }

    @DeleteMapping("/me")
    public void withdraw(Authentication authentication) {
        service.withdraw(AuthenticatedEmployer.id(authentication));
    }
}

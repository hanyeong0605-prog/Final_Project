package com.jobpilot.api.domain.auth.controller;

import com.jobpilot.api.domain.auth.dto.EmailVerificationConfirmRequest;
import com.jobpilot.api.domain.auth.dto.EmailVerificationConfirmResponse;
import com.jobpilot.api.domain.auth.dto.EmailVerificationRequest;
import com.jobpilot.api.domain.auth.service.EmailVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/email-verifications")
public class EmailVerificationController {
    private final EmailVerificationService service;

    public EmailVerificationController(EmailVerificationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendCode(@Valid @RequestBody EmailVerificationRequest request) {
        service.sendCode(request.email());
    }

    @PostMapping("/confirm")
    public EmailVerificationConfirmResponse confirmCode(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        return new EmailVerificationConfirmResponse(service.confirmCode(request.email(), request.code()));
    }
}

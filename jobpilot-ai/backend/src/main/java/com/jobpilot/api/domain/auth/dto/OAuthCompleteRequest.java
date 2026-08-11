package com.jobpilot.api.domain.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthCompleteRequest(
        @NotBlank @Size(max = 36) String ticket,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 32, max = 128) String emailVerificationToken,
        @AssertTrue boolean termsAgreed,
        @AssertTrue boolean privacyCollectionAgreed,
        boolean marketingEmailAgreed
) { }

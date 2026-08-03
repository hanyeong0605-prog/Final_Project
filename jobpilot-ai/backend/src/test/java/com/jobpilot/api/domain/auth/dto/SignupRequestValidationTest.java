package com.jobpilot.api.domain.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SignupRequestValidationTest {
    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void requiresTermsAndPrivacyConsent() {
        SignupRequest request = new SignupRequest(
                "member01", "member@example.com", "x".repeat(43), "password123", "회원", false, false, false);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("termsAgreed", "privacyCollectionAgreed");
    }

    @Test
    void requiresAtLeastSixCharactersForLoginId() {
        SignupRequest request = new SignupRequest(
                "short", "member@example.com", "x".repeat(43), "password123", "회원", true, true, false);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("loginId");
    }
}

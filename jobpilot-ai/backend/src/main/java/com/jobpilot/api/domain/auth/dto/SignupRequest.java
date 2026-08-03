package com.jobpilot.api.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(min = 6, max = 80)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "로그인 아이디는 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다.")
        String loginId,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 32, max = 128) String emailVerificationToken,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 2, max = 80) String nickname,
        @AssertTrue(message = "서비스 이용약관에 동의해 주세요.") boolean termsAgreed,
        @AssertTrue(message = "개인정보 수집 및 이용에 동의해 주세요.") boolean privacyCollectionAgreed,
        boolean marketingEmailAgreed
) {}

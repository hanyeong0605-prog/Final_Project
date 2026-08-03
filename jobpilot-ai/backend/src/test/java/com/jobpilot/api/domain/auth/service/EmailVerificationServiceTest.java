package com.jobpilot.api.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.jobpilot.api.domain.auth.entity.EmailVerification;
import com.jobpilot.api.domain.auth.exception.EmailVerificationException;
import com.jobpilot.api.domain.auth.repository.EmailVerificationRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {
    @Mock private EmailVerificationRepository verifications;
    @Mock private JavaMailSender mailSender;
    @Mock private PasswordEncoder passwordEncoder;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(
                verifications, mailSender, passwordEncoder, "sender@example.com", 10, 60, 5);
    }

    @Test
    void aConfirmedCodeCanBeConsumedOnlyOnce() {
        EmailVerification verification = new EmailVerification(
                "member@example.com", "code-hash", LocalDateTime.now().plusMinutes(10));
        when(verifications.findTopByEmailOrderByCreatedAtDesc("member@example.com"))
                .thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("123456", "code-hash")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("verification-token-hash");

        String verificationToken = service.confirmCode("MEMBER@example.com", "123456");
        when(passwordEncoder.matches(verificationToken, "verification-token-hash")).thenReturn(true);

        service.consumeVerifiedEmail("member@example.com", verificationToken);

        assertThatThrownBy(() -> service.consumeVerifiedEmail("member@example.com", verificationToken))
                .isInstanceOf(EmailVerificationException.class);
    }

    @Test
    void anIncorrectCodeIncrementsTheAttemptCount() {
        EmailVerification verification = new EmailVerification(
                "member@example.com", "code-hash", LocalDateTime.now().plusMinutes(10));
        when(verifications.findTopByEmailOrderByCreatedAtDesc("member@example.com"))
                .thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("123456", "code-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.confirmCode("member@example.com", "123456"))
                .isInstanceOf(EmailVerificationException.class);

        assertThat(verification.getFailedAttempts()).isEqualTo(1);
    }
}

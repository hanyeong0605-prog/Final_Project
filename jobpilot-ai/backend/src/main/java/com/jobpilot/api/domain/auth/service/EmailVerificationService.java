package com.jobpilot.api.domain.auth.service;

import com.jobpilot.api.domain.auth.entity.EmailVerification;
import com.jobpilot.api.domain.auth.exception.EmailDeliveryException;
import com.jobpilot.api.domain.auth.exception.EmailVerificationException;
import com.jobpilot.api.domain.auth.repository.EmailVerificationRepository;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class EmailVerificationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailVerificationRepository verifications;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final String from;
    private final long codeExpiryMinutes;
    private final long resendCooldownSeconds;
    private final int maxFailedAttempts;

    public EmailVerificationService(
            EmailVerificationRepository verifications,
            JavaMailSender mailSender,
            PasswordEncoder passwordEncoder,
            @Value("${app.mail.from:}") String from,
            @Value("${app.email-verification.code-expiry-minutes:10}") long codeExpiryMinutes,
            @Value("${app.email-verification.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${app.email-verification.max-failed-attempts:5}") int maxFailedAttempts) {
        this.verifications = verifications;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.from = from;
        this.codeExpiryMinutes = codeExpiryMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public void sendCode(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        LocalDateTime now = LocalDateTime.now();
        verifications.findTopByEmailOrderByCreatedAtDesc(email)
                .filter(verification -> verification.wasRequestedRecently(now, resendCooldownSeconds))
                .ifPresent(verification -> {
                    throw new EmailVerificationException("인증 코드는 잠시 후 다시 요청할 수 있습니다.");
                });

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        verifications.save(new EmailVerification(email, passwordEncoder.encode(code), now.plusMinutes(codeExpiryMinutes)));
        sendVerificationEmail(email, code);
    }

    public String confirmCode(String rawEmail, String code) {
        EmailVerification verification = latestVerification(rawEmail);
        LocalDateTime now = LocalDateTime.now();
        if (verification.isExpired(now)) {
            throw new EmailVerificationException("인증 코드가 만료되었습니다. 새 코드를 요청해 주세요.");
        }
        if (verification.isConsumed()) {
            throw new EmailVerificationException("이미 사용된 이메일 인증입니다. 새 코드를 요청해 주세요.");
        }
        if (verification.isVerified()) {
            throw new EmailVerificationException("이미 인증된 코드입니다. 회원가입을 완료해 주세요.");
        }
        if (verification.getFailedAttempts() >= maxFailedAttempts) {
            throw new EmailVerificationException("인증 코드 입력 횟수를 초과했습니다. 새 코드를 요청해 주세요.");
        }
        if (!verification.matchesCode(code, passwordEncoder)) {
            verification.recordFailedAttempt();
            throw new EmailVerificationException("인증 코드가 올바르지 않습니다.");
        }

        String verificationToken = newVerificationToken();
        verification.verify(passwordEncoder.encode(verificationToken));
        return verificationToken;
    }

    public void consumeVerifiedEmail(String rawEmail, String verificationToken) {
        EmailVerification verification = latestVerification(rawEmail);
        LocalDateTime now = LocalDateTime.now();
        if (verification.isExpired(now) || !verification.isVerified() || verification.isConsumed()
                || !verification.matchesVerificationToken(verificationToken, passwordEncoder)) {
            throw new EmailVerificationException("유효한 이메일 인증이 필요합니다.");
        }
        verification.consume();
    }

    private EmailVerification latestVerification(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        return verifications.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new EmailVerificationException("먼저 이메일 인증 코드를 요청해 주세요."));
    }

    private void sendVerificationEmail(String email, String code) {
        if (from.isBlank()) {
            throw new EmailDeliveryException("MAIL_USERNAME 또는 MAIL_FROM 환경변수를 설정해 주세요.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[JobPilot AI] 이메일 인증 코드");
        message.setText("JobPilot AI 회원가입 인증 코드입니다.\n\n"
                + code + "\n\n"
                + "인증 코드는 " + codeExpiryMinutes + "분 동안 유효합니다. 본인이 요청하지 않았다면 이 이메일을 무시해 주세요.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new EmailDeliveryException("인증 이메일을 보내지 못했습니다. Gmail 앱 비밀번호와 SMTP 설정을 확인해 주세요.", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String newVerificationToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

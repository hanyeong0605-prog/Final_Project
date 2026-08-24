package com.jobpilot.api.global.exception;

import com.jobpilot.api.domain.subscription.exception.SubscriptionException;
import com.jobpilot.api.domain.jobposting.provider.saramindata.exception.SaraminDataException;
import com.jobpilot.api.domain.auth.exception.DuplicateMemberException;
import com.jobpilot.api.domain.auth.exception.EmailDeliveryException;
import com.jobpilot.api.domain.auth.exception.EmailVerificationException;
import com.jobpilot.api.domain.auth.exception.InvalidCredentialsException;
import com.jobpilot.api.domain.employer.exception.DuplicateEmployerException;
import com.jobpilot.api.domain.employer.exception.EmployerNotApprovedException;
import com.jobpilot.api.domain.employer.exception.InvalidEmployerCredentialsException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    // 2026-08-19: AdminAccessService/AuthenticatedMember/AuthenticatedEmployer가 던지는
    // AccessDeniedException은 (@EnableMethodSecurity를 안 쓰기 때문에) 시큐리티 필터가
    // 아니라 컨트롤러 안에서 던져진다 - 여기서 안 잡아주면 기본 500으로 새 나가서
    // 프론트에서 "권한 없음"과 "서버 오류"를 구분할 수 없었다.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", "ACCESS_DENIED", "message", exception.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", "BAD_REQUEST", "message", exception.getMessage(), "timestamp", Instant.now().toString()));
    }
    @ExceptionHandler(DuplicateMemberException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateMember(DuplicateMemberException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "DUPLICATE_MEMBER", "message", exception.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "code", "INVALID_CREDENTIALS", "message", exception.getMessage(), "timestamp", Instant.now().toString()));
    }
    @ExceptionHandler(DuplicateEmployerException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEmployer(DuplicateEmployerException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "DUPLICATE_EMPLOYER", "message", exception.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(InvalidEmployerCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidEmployerCredentials(InvalidEmployerCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "code", "INVALID_EMPLOYER_CREDENTIALS", "message", exception.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(EmployerNotApprovedException.class)
    public ResponseEntity<Map<String, Object>> handleEmployerNotApproved(EmployerNotApprovedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", "EMPLOYER_NOT_APPROVED", "message", exception.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(EmailVerificationException.class)
    public ResponseEntity<Map<String, Object>> handleEmailVerification(EmailVerificationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", "EMAIL_VERIFICATION_FAILED", "message", exception.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<Map<String, Object>> handleEmailDelivery(EmailDeliveryException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "code", "EMAIL_DELIVERY_FAILED", "message", exception.getMessage(), "timestamp", Instant.now().toString()));
    }
    @ExceptionHandler({SaraminDataException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleProvider(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "code", "PROVIDER_SYNC_FAILED",
                "message", exception.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> handleValidation(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", "VALIDATION_FAILED",
                "message", exception.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "RESOURCE_NOT_FOUND",
                "message", exception.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(SubscriptionException.class)
    public ResponseEntity<Map<String, Object>> handleSubscription(SubscriptionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "SUBSCRIPTION_OPERATION_FAILED",
                "message", exception.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}

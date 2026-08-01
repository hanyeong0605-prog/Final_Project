package com.jobpilot.api.global.exception;

import com.jobpilot.api.domain.projectanalysis.exception.ProjectAnalysisException;
import com.jobpilot.api.domain.jobposting.provider.saramindata.exception.SaraminDataException;
import com.jobpilot.api.domain.auth.exception.DuplicateMemberException;
import com.jobpilot.api.domain.auth.exception.InvalidCredentialsException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
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
    @ExceptionHandler({SaraminDataException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleProvider(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "code", "PROVIDER_SYNC_FAILED",
                "message", exception.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ProjectAnalysisException.class)
    public ResponseEntity<Map<String, Object>> handleProjectAnalysis(ProjectAnalysisException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", "PROJECT_ANALYSIS_FAILED",
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
}

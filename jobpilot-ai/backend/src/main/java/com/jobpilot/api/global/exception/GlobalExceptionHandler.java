package com.jobpilot.api.global.exception;

import com.jobpilot.api.domain.projectanalysis.exception.ProjectAnalysisException;
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

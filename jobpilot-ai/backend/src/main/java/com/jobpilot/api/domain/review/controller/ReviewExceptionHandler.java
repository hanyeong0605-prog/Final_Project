package com.jobpilot.api.domain.review.controller;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Review-scoped errors: don't change error contracts of existing product APIs. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = CompanyReviewController.class)
public class ReviewExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("code", "REVIEW_REQUEST_FAILED",
                "message", ex.getReason() == null ? "요청을 처리할 수 없습니다." : ex.getReason()));
    }
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, String>> conflict(RuntimeException ex) {
        // Concurrent duplicate creates and concurrent edits must not reveal SQL or review text.
        return ResponseEntity.status(409).body(Map.of("code", "REVIEW_CONFLICT",
                "message", "리뷰 상태가 변경되었거나 이미 작성한 리뷰가 있습니다. 새로고침 후 확인하세요."));
    }
}

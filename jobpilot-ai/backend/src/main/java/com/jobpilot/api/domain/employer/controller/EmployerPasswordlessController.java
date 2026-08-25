package com.jobpilot.api.domain.employer.controller;

import com.jobpilot.api.domain.employer.dto.EmployerEnrollmentRequest;
import com.jobpilot.api.domain.employer.dto.EmployerPasswordlessRequest;
import com.jobpilot.api.domain.employer.dto.EmployerPasswordlessResultRequest;
import com.jobpilot.api.domain.employer.service.EmployerPasswordlessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employer/passwordless")
public class EmployerPasswordlessController {
    private final EmployerPasswordlessService service;
    public EmployerPasswordlessController(EmployerPasswordlessService service) { this.service = service; }

    @PostMapping("/enrollment")
    public Map<String, Object> enrollment(@Valid @RequestBody EmployerEnrollmentRequest request) { return service.enrollment(request.loginId(), request.password()); }
    @PostMapping("/enrollment/status")
    public Map<String, Object> enrollmentStatus(@Valid @RequestBody EmployerEnrollmentRequest request) { return service.enrollmentStatus(request.loginId(), request.password()); }
    @PostMapping("/start")
    public Map<String, Object> start(@Valid @RequestBody EmployerPasswordlessRequest request, HttpServletRequest http) { return service.start(request.loginId(), clientIp(http)); }
    @PostMapping("/result")
    public Object result(@Valid @RequestBody EmployerPasswordlessResultRequest request) { return service.result(request.loginId(), request.sessionId()); }
    @PostMapping("/cancel")
    public Map<String, Object> cancel(@Valid @RequestBody EmployerPasswordlessResultRequest request) { return service.cancel(request.loginId(), request.sessionId()); }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}

package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.member.dto.QnetQualificationResponse;
import com.jobpilot.api.domain.member.service.QnetQualificationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/certifications")
public class QnetQualificationController {
    private final QnetQualificationService service;

    public QnetQualificationController(QnetQualificationService service) { this.service = service; }

    @GetMapping("/catalog")
    public List<QnetQualificationResponse> catalog(@RequestParam String query) {
        return service.search(query);
    }
}

package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.member.dto.QnetQualificationDetailResponse;
import com.jobpilot.api.domain.member.dto.QnetQualificationResponse;
import com.jobpilot.api.domain.member.service.QnetQualificationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    // 2026-08-11: 자격증 카드/검색결과에서 "상세보기" 눌렀을 때 - 종목코드(catalog()가
    // 돌려주는 code 필드)로 올해 시험일정 + 응시 수수료를 조회한다.
    @GetMapping("/catalog/{jmcd}/detail")
    public QnetQualificationDetailResponse detail(@PathVariable String jmcd) {
        return service.detail(jmcd);
    }
}

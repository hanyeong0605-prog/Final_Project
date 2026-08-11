package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.member.dto.CertificateAuthenticityResponse;
import com.jobpilot.api.domain.member.dto.QnetQualificationDetailResponse;
import com.jobpilot.api.domain.member.dto.QnetQualificationResponse;
import com.jobpilot.api.domain.member.service.KcaCertificateAuthenticityService;
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
    private final KcaCertificateAuthenticityService authenticityService;

    public QnetQualificationController(QnetQualificationService service, KcaCertificateAuthenticityService authenticityService) {
        this.service = service;
        this.authenticityService = authenticityService;
    }

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

    // 2026-08-11: KCA 국가기술자격증(개인) 진위여부 확인 - 자격증 발급번호만으로 조회한다
    // (무선설비/통신설비/전파전자/정보통신 분야만 커버, KcaCertificateAuthenticityService 참고).
    @GetMapping("/authenticity")
    public CertificateAuthenticityResponse authenticity(@RequestParam String no) {
        return new CertificateAuthenticityResponse(authenticityService.checkAuthenticity(no));
    }
}

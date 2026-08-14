package com.jobpilot.api.domain.portfolio.controller;

import com.jobpilot.api.domain.portfolio.dto.PortfolioDocumentSummaryResponse;
import com.jobpilot.api.domain.portfolio.dto.PortfolioGenerateRequest;
import com.jobpilot.api.domain.portfolio.service.PortfolioDocumentService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// /api/v1/members/me/** 관례(SelfIntroductionController 참고)를 그대로 따른다 - 로그인
// 회원만 접근 가능하고 SecurityConfig의 anyRequest().authenticated()로 이미 막혀 있어
// 별도 게이팅 코드가 필요 없다. 생성 엔드포인트만 GitHub 코드 분석 화면과의 연속성을 위해
// /api/v1/project-analysis 아래 둔다.
@RestController
public class PortfolioDocumentController {
    private final PortfolioDocumentService service;

    public PortfolioDocumentController(PortfolioDocumentService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/project-analysis/portfolio")
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioDocumentSummaryResponse generate(Authentication auth, @Valid @RequestBody PortfolioGenerateRequest request) {
        return service.generate(AuthenticatedMember.id(auth), request);
    }

    @GetMapping("/api/v1/members/me/portfolio-documents")
    public List<PortfolioDocumentSummaryResponse> list(Authentication auth) {
        return service.list(AuthenticatedMember.id(auth));
    }

    @GetMapping("/api/v1/members/me/portfolio-documents/{id}/pptx")
    public ResponseEntity<byte[]> pptx(Authentication auth, @PathVariable Long id) {
        Long memberId = AuthenticatedMember.id(auth);
        return download(service.pptx(memberId, id), service.title(memberId, id) + ".pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    @GetMapping("/api/v1/members/me/portfolio-documents/{id}/pdf")
    public ResponseEntity<byte[]> pdf(Authentication auth, @PathVariable Long id) {
        Long memberId = AuthenticatedMember.id(auth);
        return download(service.pdf(memberId, id), service.title(memberId, id) + ".pdf", "application/pdf");
    }

    private ResponseEntity<byte[]> download(byte[] data, String filename, String contentType) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(contentType))
                .body(data);
    }
}

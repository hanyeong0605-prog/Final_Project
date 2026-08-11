package com.jobpilot.api.domain.member.controller;

import com.jobpilot.api.domain.member.dto.CertificateBookmarkRequest;
import com.jobpilot.api.domain.member.dto.QnetFieldCountResponse;
import com.jobpilot.api.domain.member.dto.QnetQualificationDetailResponse;
import com.jobpilot.api.domain.member.dto.QnetQualificationPageResponse;
import com.jobpilot.api.domain.member.dto.QnetQualificationResponse;
import com.jobpilot.api.domain.member.service.CertificateBookmarkService;
import com.jobpilot.api.domain.member.service.CertificateRecommendationService;
import com.jobpilot.api.domain.member.service.QnetQualificationService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/certifications")
public class QnetQualificationController {
    private final QnetQualificationService service;
    private final CertificateBookmarkService bookmarkService;
    private final CertificateRecommendationService recommendationService;

    public QnetQualificationController(QnetQualificationService service,
            CertificateBookmarkService bookmarkService, CertificateRecommendationService recommendationService) {
        this.service = service;
        this.bookmarkService = bookmarkService;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/catalog")
    public List<QnetQualificationResponse> catalog(@RequestParam String query) {
        return service.search(query);
    }

    // 2026-08-11: "성장 기회 추천" 페이지 - 검색어 없이 전체 종목을 이름순으로 훑어보는
    // 목록(24건씩). 공개 참고데이터라 catalog()처럼 로그인 불필요. field를 넘기면 NCS
    // 직무분야가 정확히 일치하는 종목만 남긴다(비우면 전체). "분야별 버튼" 요청으로
    // 단일 IT 체크박스(itOnly)에서 임의 분야 하나를 고르는 방식으로 바꿨다.
    @GetMapping("/catalog/list")
    public QnetQualificationPageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) String field) {
        return service.list(page, size, field);
    }

    // 2026-08-11: "전체 자격증 목록" 위 분야별 필터 버튼용 - 카탈로그에 실제 존재하는
    // field 값 + 건수 목록. 프론트가 이 목록으로 버튼을 만든다(하드코딩 없이 실데이터 기준).
    @GetMapping("/catalog/fields")
    public List<QnetFieldCountResponse> fields() {
        return service.fields();
    }

    // 2026-08-11: 자격증 카드/검색결과에서 "상세보기" 눌렀을 때 - 종목코드(catalog()가
    // 돌려주는 code 필드)로 올해 시험일정 + 응시 수수료를 조회한다.
    @GetMapping("/catalog/{jmcd}/detail")
    public QnetQualificationDetailResponse detail(@PathVariable String jmcd) {
        return service.detail(jmcd);
    }

    // 2026-08-11: "성장 기회 추천" 페이지 - 찜한 자격증 목록/추가/삭제. 로그인 회원 개인
    // 데이터라 SecurityConfig에서 별도 permitAll 없이 기본 authenticated() 규칙을 탄다.
    @GetMapping("/bookmarks")
    public List<QnetQualificationResponse> bookmarks(Authentication auth) {
        return bookmarkService.list(AuthenticatedMember.id(auth));
    }

    @PostMapping("/bookmarks")
    public List<QnetQualificationResponse> addBookmark(Authentication auth, @Valid @RequestBody CertificateBookmarkRequest request) {
        return bookmarkService.add(AuthenticatedMember.id(auth), request);
    }

    @DeleteMapping("/bookmarks/{jmcd}")
    public List<QnetQualificationResponse> removeBookmark(Authentication auth, @PathVariable String jmcd) {
        return bookmarkService.remove(AuthenticatedMember.id(auth), jmcd);
    }

    // 2026-08-11: 목표 직무분야 기준 1차 필터 추천(CertificateRecommendationService 참고) -
    // 보유/찜한 종목은 제외하고 최대 12건.
    @GetMapping("/recommended")
    public List<QnetQualificationResponse> recommended(Authentication auth) {
        return recommendationService.recommend(AuthenticatedMember.id(auth));
    }
}

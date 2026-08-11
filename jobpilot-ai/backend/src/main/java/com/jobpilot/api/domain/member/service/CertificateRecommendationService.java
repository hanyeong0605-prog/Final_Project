package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.member.dto.QnetQualificationResponse;
import com.jobpilot.api.domain.member.entity.MemberProfile;
import com.jobpilot.api.domain.member.repository.CertificateBookmarkRepository;
import com.jobpilot.api.domain.member.repository.CertificateRepository;
import com.jobpilot.api.domain.member.repository.MemberProfileRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 2026-08-11: "성장 기회 추천" 페이지의 "추천 자격증" 섹션 - 회원의 목표 직무분야를
 * 기준으로 아직 안 딴(보유/찜 목록에 없는) Q-Net 자격 종목을 추려서 보여준다.
 *
 * v1은 정식 gap-analysis 엔진(#69 누적 인사이트, 기술스택/면접 피드백 기반)과는 별도로
 * 동작한다 - 자격증 종목까지 다루려면 그 엔진 자체를 확장해야 하는데 범위가 커서,
 * 우선 회원 목표 직무분야(프론트 profileCatalog.ts의 jobFamilies 8개)를 Q-Net
 * 대직무분야명(obligfldnm)으로 손으로 매핑한 키워드 필터로 시작한다. 나중에 실제
 * 매칭/부족역량 분석과 통합할 여지를 남겨 둔 것 - 정교한 추천이 아니라 "이 분야면 이런
 * 자격증도 있어요" 수준의 1차 필터.
 */
@Service
public class CertificateRecommendationService {
    private static final int MAX_RECOMMENDATIONS = 12;
    private static final Map<String, List<String>> JOB_FAMILY_TO_QNET_FIELD = Map.ofEntries(
            Map.entry("IT 개발·데이터", List.of("정보통신")),
            Map.entry("기획·PM", List.of("정보통신", "경영·회계·사무")),
            Map.entry("디자인", List.of("문화·예술·디자인·방송")),
            Map.entry("마케팅·홍보", List.of("경영·회계·사무", "문화·예술·디자인·방송")),
            Map.entry("경영·사무", List.of("경영·회계·사무")),
            Map.entry("영업·고객상담", List.of("영업·판매", "경영·회계·사무")),
            Map.entry("금융·회계", List.of("경영·회계·사무", "금융·보험")),
            Map.entry("교육·연구", List.of("교육·자연·사회과학"))
    );

    private final MemberProfileRepository profiles;
    private final CertificateRepository ownedCertificates;
    private final CertificateBookmarkRepository bookmarks;
    private final QnetQualificationService qnetQualificationService;

    public CertificateRecommendationService(MemberProfileRepository profiles, CertificateRepository ownedCertificates,
            CertificateBookmarkRepository bookmarks, QnetQualificationService qnetQualificationService) {
        this.profiles = profiles;
        this.ownedCertificates = ownedCertificates;
        this.bookmarks = bookmarks;
        this.qnetQualificationService = qnetQualificationService;
    }

    public List<QnetQualificationResponse> recommend(Long memberId) {
        MemberProfile profile = profiles.findById(memberId).orElse(null);
        if (profile == null) return List.of();
        List<String> keywords = JOB_FAMILY_TO_QNET_FIELD.getOrDefault(profile.getTargetJobFamily(), List.of());
        if (keywords.isEmpty()) return List.of();

        Set<String> exclude = excludedNames(memberId);
        return qnetQualificationService.catalogSnapshot().stream()
                .filter(item -> matches(item, keywords))
                .filter(item -> !exclude.contains(normalize(item.name())))
                .limit(MAX_RECOMMENDATIONS)
                .toList();
    }

    private boolean matches(QnetQualificationResponse item, List<String> keywords) {
        String haystack = normalize(item.field() + " " + item.subField());
        return keywords.stream().anyMatch(keyword -> haystack.contains(normalize(keyword)));
    }

    private Set<String> excludedNames(Long memberId) {
        Set<String> names = new HashSet<>();
        ownedCertificates.findByMemberId(memberId).forEach(certificate -> names.add(normalize(certificate.getName())));
        bookmarks.findByMemberIdOrderByIdDesc(memberId).forEach(bookmark -> names.add(normalize(bookmark.getName())));
        return names;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }
}

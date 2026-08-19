package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.matching.service.JobMatchRefreshScheduler;
import com.jobpilot.api.domain.member.dto.MemberCertificateRequest;
import com.jobpilot.api.domain.member.dto.MemberCertificateResponse;
import com.jobpilot.api.domain.member.entity.Certificate;
import com.jobpilot.api.domain.member.repository.CertificateRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class MemberCertificateService {
    private static final int MAX_CERTIFICATES = 20;

    private final CertificateRepository certificates;
    private final JobMatchRefreshScheduler matchRefreshScheduler;

    public MemberCertificateService(CertificateRepository certificates, JobMatchRefreshScheduler matchRefreshScheduler) {
        this.certificates = certificates;
        this.matchRefreshScheduler = matchRefreshScheduler;
    }

    public List<MemberCertificateResponse> get(Long memberId) {
        return certificates.findByMemberId(memberId).stream().map(this::toResponse).toList();
    }

    public List<MemberCertificateResponse> replace(Long memberId, List<MemberCertificateRequest> requested) {
        List<MemberCertificateRequest> input = requested == null ? List.of() : requested;
        if (input.size() > MAX_CERTIFICATES) {
            throw new IllegalArgumentException("보유 자격증은 최대 20개까지 저장할 수 있습니다.");
        }
        for (MemberCertificateRequest item : input) {
            if (item.expiresAt() != null && item.acquiredAt() != null && item.expiresAt().isBefore(item.acquiredAt())) {
                throw new IllegalArgumentException("자격증 만료일은 취득일보다 빠를 수 없습니다.");
            }
        }

        LinkedHashMap<String, MemberCertificateRequest> normalized = new LinkedHashMap<>();
        for (MemberCertificateRequest item : input) {
            String name = canonicalName(item.name());
            normalized.putIfAbsent(key(name), new MemberCertificateRequest(name, canonicalIssuer(item.issuer()), item.acquiredAt(), item.expiresAt(), item.officialUrl()));
        }
        certificates.deleteByMemberId(memberId);
        certificates.flush();
        List<Certificate> saved = certificates.saveAll(normalized.values().stream()
                .map(item -> new Certificate(memberId, clean(item.name()), clean(item.issuer()), item.acquiredAt(),
                        item.expiresAt(), clean(item.officialUrl())))
                .toList());
        matchRefreshScheduler.enqueueForMember(memberId);
        return saved.stream().map(this::toResponse).toList();
    }

    private MemberCertificateResponse toResponse(Certificate certificate) {
        return new MemberCertificateResponse(certificate.getId(), certificate.getName(), certificate.getIssuer(),
                certificate.getAcquiredAt(), certificate.getExpiresAt(), certificate.getOfficialUrl());
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    private String canonicalName(String value) {
        String raw = value == null ? "" : value.trim(); String compact = key(raw);
        String driver = driverKind(compact); if (!driver.isBlank()) return "자동차운전면허 " + driver;
        if (compact.equals("운전면허") || compact.equals("자동차운전면허")) return "자동차운전면허 (종류 미확인)";
        if (compact.startsWith("itq")) { if (compact.contains("파워포인트")) return "ITQ 한글파워포인트"; if (compact.contains("엑셀")) return "ITQ 한글엑셀"; if (compact.contains("아래한글") || compact.contains("한글")) return "ITQ 아래한글"; if (compact.contains("인터넷")) return "ITQ 인터넷"; }
        if (compact.startsWith("gtq")) { Matcher grade = Pattern.compile("([123])급").matcher(compact); return "GTQ 그래픽기술자격" + (grade.find() ? " " + grade.group(1) + "급" : ""); }
        if (compact.contains("컴퓨터활용능력") || compact.startsWith("컴활")) { Matcher grade = Pattern.compile("([12])급").matcher(compact); return "컴퓨터활용능력" + (grade.find() ? " " + grade.group(1) + "급" : ""); }
        if (compact.equals("sqld") || compact.equals("sql개발자")) return "SQLD"; if (compact.equals("sqlp") || compact.equals("sql전문가")) return "SQLP";
        if (compact.equals("opic") || compact.equals("오픽")) return "OPIc"; if (compact.equals("toeic") || compact.equals("토익")) return "TOEIC";
        return raw;
    }
    private String driverKind(String compact) { compact = compact.replace("제", ""); if (compact.contains("대형견인")) return "1종 특수 대형견인"; if (compact.contains("소형견인")) return "1종 특수 소형견인"; if (compact.contains("구난")) return "1종 특수 구난"; if (compact.contains("1종대형") || compact.equals("대형면허")) return "1종 대형"; if (compact.contains("1종보통") || compact.equals("보통1종")) return "1종 보통"; if (compact.contains("2종소형")) return "2종 소형"; if (compact.contains("2종보통") || compact.equals("보통2종")) return "2종 보통"; if (compact.contains("원동기")) return "원동기장치자전거"; return ""; }
    private String canonicalIssuer(String value) { String raw = clean(value); String compact = key(raw); if (compact.equals("산업인력공단") || compact.equals("hrdk") || compact.contains("한국산업인력공단")) return "한국산업인력공단"; if (compact.equals("kpc") || compact.contains("한국생산성본부")) return "한국생산성본부"; if (compact.contains("도로교통공단")) return "도로교통공단"; if (compact.equals("k-data") || compact.contains("한국데이터산업진흥원")) return "한국데이터산업진흥원"; if (compact.contains("대한상공회의소") || compact.equals("상공회의소")) return "대한상공회의소"; return raw; }
    private String key(String value) { return value == null ? "" : value.replaceAll("[\\s._/()\\-]", "").toLowerCase(Locale.ROOT); }
}

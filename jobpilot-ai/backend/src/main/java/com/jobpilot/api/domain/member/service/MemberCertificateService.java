package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.matching.service.JobMatchRefreshScheduler;
import com.jobpilot.api.domain.member.dto.MemberCertificateRequest;
import com.jobpilot.api.domain.member.dto.MemberCertificateResponse;
import com.jobpilot.api.domain.member.entity.Certificate;
import com.jobpilot.api.domain.member.repository.CertificateRepository;
import jakarta.transaction.Transactional;
import java.util.List;
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

        certificates.deleteByMemberId(memberId);
        certificates.flush();
        List<Certificate> saved = certificates.saveAll(input.stream()
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
}

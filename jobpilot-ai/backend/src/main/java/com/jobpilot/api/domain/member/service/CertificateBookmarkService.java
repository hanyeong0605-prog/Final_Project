package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.member.dto.CertificateBookmarkRequest;
import com.jobpilot.api.domain.member.dto.QnetQualificationResponse;
import com.jobpilot.api.domain.member.entity.CertificateBookmark;
import com.jobpilot.api.domain.member.repository.CertificateBookmarkRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 2026-08-11: "성장 기회 추천" 페이지 - 회원이 찜해 둔 Q-Net 자격 종목 CRUD.
 * 응답 모양을 {@link QnetQualificationResponse}(카탈로그 검색 결과와 동일)로 맞춰서,
 * 프론트에서 검색 결과 카드/상세보기 모달을 그대로 재사용할 수 있게 한다.
 */
@Service
@Transactional
public class CertificateBookmarkService {
    private static final int MAX_BOOKMARKS = 50;

    private final CertificateBookmarkRepository repository;

    public CertificateBookmarkService(CertificateBookmarkRepository repository) {
        this.repository = repository;
    }

    public List<QnetQualificationResponse> list(Long memberId) {
        return repository.findByMemberIdOrderByIdDesc(memberId).stream().map(this::toResponse).toList();
    }

    public List<QnetQualificationResponse> add(Long memberId, CertificateBookmarkRequest request) {
        if (repository.findByMemberIdAndJmcd(memberId, request.jmcd()).isEmpty()) {
            if (repository.findByMemberIdOrderByIdDesc(memberId).size() >= MAX_BOOKMARKS) {
                throw new IllegalArgumentException("찜한 자격증은 최대 " + MAX_BOOKMARKS + "개까지 저장할 수 있습니다.");
            }
            repository.save(new CertificateBookmark(memberId, request.jmcd(), request.name(),
                    blankToNull(request.qualificationType()), blankToNull(request.field()), blankToNull(request.subField())));
        }
        return list(memberId);
    }

    public List<QnetQualificationResponse> remove(Long memberId, String jmcd) {
        repository.deleteByMemberIdAndJmcd(memberId, jmcd);
        return list(memberId);
    }

    private QnetQualificationResponse toResponse(CertificateBookmark bookmark) {
        return new QnetQualificationResponse(bookmark.getJmcd(), bookmark.getName(),
                bookmark.getQualificationType(), bookmark.getField(), bookmark.getSubField());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.jobpilot.api.domain.member.repository;

import com.jobpilot.api.domain.member.entity.CertificateBookmark;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateBookmarkRepository extends JpaRepository<CertificateBookmark, Long> {
    List<CertificateBookmark> findByMemberIdOrderByIdDesc(Long memberId);
    Optional<CertificateBookmark> findByMemberIdAndJmcd(Long memberId, String jmcd);
    Optional<CertificateBookmark> findByIdAndMemberId(Long id, Long memberId);
    void deleteByMemberIdAndJmcd(Long memberId, String jmcd);
}

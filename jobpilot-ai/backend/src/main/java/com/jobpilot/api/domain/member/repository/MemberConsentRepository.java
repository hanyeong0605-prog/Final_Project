package com.jobpilot.api.domain.member.repository;

import com.jobpilot.api.domain.member.entity.MemberConsent;
import com.jobpilot.api.domain.member.entity.ConsentType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberConsentRepository extends JpaRepository<MemberConsent, Long> {
    Optional<MemberConsent> findByMemberIdAndConsentType(Long memberId, ConsentType consentType);
}

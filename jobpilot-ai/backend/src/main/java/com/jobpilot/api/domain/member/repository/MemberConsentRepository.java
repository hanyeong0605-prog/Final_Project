package com.jobpilot.api.domain.member.repository;

import com.jobpilot.api.domain.member.entity.MemberConsent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberConsentRepository extends JpaRepository<MemberConsent, Long> {}

package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.MemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {}

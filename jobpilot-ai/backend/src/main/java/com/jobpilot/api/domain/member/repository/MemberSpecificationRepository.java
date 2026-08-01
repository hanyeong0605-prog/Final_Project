package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.MemberSpecification;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemberSpecificationRepository extends JpaRepository<MemberSpecification, Long> {}

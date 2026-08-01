package com.jobpilot.api.domain.member.repository;
import com.jobpilot.api.domain.member.entity.Certificate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    List<Certificate> findByMemberId(Long memberId);
}

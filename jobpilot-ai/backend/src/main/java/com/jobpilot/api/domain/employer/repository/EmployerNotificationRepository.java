package com.jobpilot.api.domain.employer.repository;

import com.jobpilot.api.domain.employer.entity.EmployerNotification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployerNotificationRepository extends JpaRepository<EmployerNotification, Long> {
    List<EmployerNotification> findTop30ByEmployerAccountIdOrderByCreatedAtDesc(Long employerAccountId);
    long countByEmployerAccountIdAndReadFalse(Long employerAccountId);
    Optional<EmployerNotification> findByIdAndEmployerAccountId(Long id, Long employerAccountId);
}

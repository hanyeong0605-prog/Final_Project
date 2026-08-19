package com.jobpilot.api.domain.employer.repository;

import com.jobpilot.api.domain.employer.entity.EmployerAccount;
import com.jobpilot.api.domain.employer.entity.EmployerAccountStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployerAccountRepository extends JpaRepository<EmployerAccount, Long> {
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
    boolean existsByBusinessRegistrationNumber(String businessRegistrationNumber);
    Optional<EmployerAccount> findByLoginId(String loginId);

    Page<EmployerAccount> findByStatus(EmployerAccountStatus status, Pageable pageable);
    long countByStatus(EmployerAccountStatus status);
    Page<EmployerAccount> findByCompanyNameContainingIgnoreCaseOrManagerNameContainingIgnoreCaseOrBusinessRegistrationNumberContaining(
            String companyName, String managerName, String businessRegistrationNumber, Pageable pageable);
}

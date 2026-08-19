package com.jobpilot.api.domain.employer.service;

import com.jobpilot.api.domain.employer.entity.EmployerAccount;
import com.jobpilot.api.domain.employer.entity.EmployerAccountStatus;
import com.jobpilot.api.domain.employer.exception.EmployerNotApprovedException;
import com.jobpilot.api.domain.employer.repository.EmployerAccountRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

/** AdminAccessService의 기업회원 버전 - 승인(APPROVED)된 기업회원만 채용공고를 등록/관리할 수 있게 막는다. */
@Service
public class EmployerAccessService {
    private final EmployerAccountRepository employers;

    public EmployerAccessService(EmployerAccountRepository employers) {
        this.employers = employers;
    }

    public EmployerAccount requireApproved(Long employerId) {
        EmployerAccount employer = employers.findById(employerId)
                .orElseThrow(() -> new ResourceNotFoundException("기업회원을 찾을 수 없습니다."));
        if (employer.getStatus() != EmployerAccountStatus.APPROVED) {
            throw new EmployerNotApprovedException("승인이 완료된 기업회원만 채용공고를 등록할 수 있습니다.");
        }
        return employer;
    }
}

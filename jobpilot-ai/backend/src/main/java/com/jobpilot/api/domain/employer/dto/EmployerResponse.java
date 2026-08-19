package com.jobpilot.api.domain.employer.dto;

import com.jobpilot.api.domain.employer.entity.EmployerAccount;
import com.jobpilot.api.domain.employer.entity.EmployerAccountStatus;

public record EmployerResponse(
        Long id, String loginId, String email, String managerName, String managerPhone,
        String companyName, String businessRegistrationNumber, String representativeName,
        String openingDate, String companyAddress, boolean ntsVerified,
        EmployerAccountStatus status, String rejectionReason
) {
    public static EmployerResponse from(EmployerAccount employer) {
        return new EmployerResponse(
                employer.getId(), employer.getLoginId(), employer.getEmail(), employer.getManagerName(),
                employer.getManagerPhone(), employer.getCompanyName(), employer.getBusinessRegistrationNumber(),
                employer.getRepresentativeName(), employer.getOpeningDate(), employer.getCompanyAddress(),
                employer.isNtsVerified(), employer.getStatus(), employer.getRejectionReason());
    }
}

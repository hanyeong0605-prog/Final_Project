package com.jobpilot.api.domain.member.dto;

import jakarta.validation.constraints.Size;

public record WithdrawalRequest(
        @Size(max = 72) String password,
        @Size(max = 20) String confirmationText
) {}

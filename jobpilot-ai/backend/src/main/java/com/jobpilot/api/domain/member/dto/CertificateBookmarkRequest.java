package com.jobpilot.api.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CertificateBookmarkRequest(
        @NotBlank @Size(max = 20) String jmcd,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 100) String qualificationType,
        @Size(max = 200) String field,
        @Size(max = 200) String subField
) {}

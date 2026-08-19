package com.jobpilot.api.domain.resume.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.api.domain.resume.entity.ResumeEntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResumeEntryRequest(@NotNull ResumeEntryType entryType, @NotBlank @Size(max = 255) String title,
                                 @NotNull JsonNode content, int displayOrder) {}

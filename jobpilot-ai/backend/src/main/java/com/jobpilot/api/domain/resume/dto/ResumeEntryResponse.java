package com.jobpilot.api.domain.resume.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.api.domain.resume.entity.ResumeEntry;
import java.time.LocalDateTime;

public record ResumeEntryResponse(Long id, String entryType, String title, JsonNode content, int displayOrder,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ResumeEntryResponse from(ResumeEntry value) {
        return new ResumeEntryResponse(value.getId(), value.getEntryType().name(), value.getTitle(), value.getContent(), value.getDisplayOrder(), value.getCreatedAt(), value.getUpdatedAt());
    }
}

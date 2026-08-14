package com.jobpilot.api.domain.resume.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.api.domain.resume.entity.ResumeDocument;
import java.time.LocalDateTime;
public record ResumeDocumentResponse(Long id, String type, String title, String originalFilename, String templateKey,
        String extractedText, String generatedContent, JsonNode extractedProfile, LocalDateTime createdAt) {
    public static ResumeDocumentResponse from(ResumeDocument value) {
        return new ResumeDocumentResponse(value.getId(), value.getDocumentType().name(), value.getTitle(), value.getOriginalFilename(), value.getTemplateKey(),
                value.getExtractedText(), value.getGeneratedContent(), value.getStructuredContent(), value.getCreatedAt());
    }
}

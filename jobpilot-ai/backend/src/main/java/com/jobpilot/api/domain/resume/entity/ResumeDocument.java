package com.jobpilot.api.domain.resume.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resume_documents")
public class ResumeDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false, length = 24) private ResumeDocumentType documentType;
    @Column(nullable = false) private String title;
    @Column(name = "original_filename", length = 500) private String originalFilename;
    @Column(name = "template_key", length = 40) private String templateKey;
    @Lob @Column(name = "extracted_text", columnDefinition = "LONGTEXT") private String extractedText;
    @Lob @Column(name = "generated_content", columnDefinition = "LONGTEXT") private String generatedContent;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "structured_content", columnDefinition = "json") private JsonNode structuredContent;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected ResumeDocument() {}
    public ResumeDocument(Long memberId, ResumeDocumentType type, String title, String filename, String extractedText, String generatedContent, JsonNode structuredContent) {
        this(memberId, type, title, filename, extractedText, generatedContent, structuredContent, null);
    }
    public ResumeDocument(Long memberId, ResumeDocumentType type, String title, String filename, String extractedText, String generatedContent, JsonNode structuredContent, String templateKey) {
        this.memberId = memberId; this.documentType = type; this.title = title; this.originalFilename = filename;
        this.extractedText = extractedText; this.generatedContent = generatedContent; this.structuredContent = structuredContent; this.templateKey = templateKey;
        this.createdAt = LocalDateTime.now(); this.updatedAt = this.createdAt;
    }
    public Long getId() { return id; } public Long getMemberId() { return memberId; }
    public ResumeDocumentType getDocumentType() { return documentType; } public String getTitle() { return title; }
    public String getOriginalFilename() { return originalFilename; } public String getExtractedText() { return extractedText; }
    public String getTemplateKey() { return templateKey; }
    public String getGeneratedContent() { return generatedContent; } public JsonNode getStructuredContent() { return structuredContent; }
    public LocalDateTime getCreatedAt() { return createdAt; } public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void rename(String value) { this.title = value; this.updatedAt = LocalDateTime.now(); }
    public void updateStructuredContent(JsonNode value) { this.structuredContent = value; this.updatedAt = LocalDateTime.now(); }
}

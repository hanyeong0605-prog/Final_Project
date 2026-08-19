package com.jobpilot.api.domain.resume.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resume_entries")
public class ResumeEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Enumerated(EnumType.STRING) @Column(name = "entry_type", nullable = false, length = 30) private ResumeEntryType entryType;
    @Column(nullable = false, length = 255) private String title;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json") private JsonNode content;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected ResumeEntry() {}
    public ResumeEntry(Long memberId, ResumeEntryType entryType, String title, JsonNode content, int displayOrder) {
        this.memberId = memberId; this.entryType = entryType; this.title = title; this.content = content; this.displayOrder = displayOrder;
        this.createdAt = LocalDateTime.now(); this.updatedAt = this.createdAt;
    }
    public void update(String title, JsonNode content, int displayOrder) { this.title = title; this.content = content; this.displayOrder = displayOrder; this.updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; } public Long getMemberId() { return memberId; } public ResumeEntryType getEntryType() { return entryType; }
    public String getTitle() { return title; } public JsonNode getContent() { return content; } public int getDisplayOrder() { return displayOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; } public LocalDateTime getUpdatedAt() { return updatedAt; }
}

package com.jobpilot.api.domain.resume.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_resume_save_states")
public class MemberResumeSaveState {
    @Id @Column(name = "member_id") private Long memberId;
    @Column(name = "save_status", nullable = false, length = 20) private String saveStatus;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected MemberResumeSaveState() {}
    public MemberResumeSaveState(Long memberId, String saveStatus) { this.memberId = memberId; this.saveStatus = saveStatus; this.updatedAt = LocalDateTime.now(); }
    public void update(String saveStatus) { this.saveStatus = saveStatus; this.updatedAt = LocalDateTime.now(); }
    public String getSaveStatus() { return saveStatus; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

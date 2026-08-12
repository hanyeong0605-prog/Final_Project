package com.jobpilot.api.domain.matching.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_job_events")
public class MemberJobEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "job_posting_id", nullable = false) private Long jobPostingId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected MemberJobEvent() {}
    public MemberJobEvent(Long memberId, Long jobPostingId, String eventType) {
        this.memberId = memberId;
        this.jobPostingId = jobPostingId;
        this.eventType = eventType;
        this.createdAt = LocalDateTime.now();
    }
}

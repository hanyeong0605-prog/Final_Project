package com.jobpilot.api.domain.interest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_interests")
public class UserInterest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "target_type", nullable = false) private String targetType;
    @Column(name = "target_id", nullable = false) private Long targetId;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected UserInterest() {}

    public UserInterest(Long memberId, String targetType, Long targetId) {
        this.memberId = memberId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getTargetId() { return targetId; }
}

package com.jobpilot.api.domain.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_daily_visits", uniqueConstraints = @UniqueConstraint(name = "uq_member_daily_visits_member_date", columnNames = {"member_id", "visit_date"}))
public class MemberDailyVisit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "visit_date", nullable = false) private LocalDate visitDate;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected MemberDailyVisit() {}

    public MemberDailyVisit(Long memberId, LocalDate visitDate) {
        this.memberId = memberId;
        this.visitDate = visitDate;
        this.createdAt = LocalDateTime.now();
    }
}

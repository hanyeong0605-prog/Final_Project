package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "education_histories")
public class EducationHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(nullable = false) private String title;
    private String provider;
    @Column(name = "started_at") private LocalDate startedAt;
    @Column(name = "ended_at") private LocalDate endedAt;
    @Column(name = "result_url", length = 1000) private String resultUrl;
    protected EducationHistory() {}
    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getTitle() { return title; }
}

package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "projects")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(nullable = false) private String title;
    @Lob @Column(name = "role_description", columnDefinition = "TEXT") private String roleDescription;
    @Lob @Column(name = "problem_description", columnDefinition = "TEXT") private String problemDescription;
    @Lob @Column(name = "solution_description", columnDefinition = "TEXT") private String solutionDescription;
    @Lob @Column(name = "result_description", columnDefinition = "TEXT") private String resultDescription;
    @Column(name = "github_url", length = 1000) private String githubUrl;
    @Column(name = "deployment_url", length = 1000) private String deploymentUrl;
    @Column(name = "started_at") private LocalDate startedAt;
    @Column(name = "ended_at") private LocalDate endedAt;
    protected Project() {}
    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getTitle() { return title; }
}

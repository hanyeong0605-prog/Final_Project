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
    // 2026-08-10: 이력서 작성 도우미(태스크 #58) - 엔티티/레포지토리는 있었지만
    // Service/Controller가 없어서 안 쓰이고 있었다. SelfIntroduction과 같은 이유로 생성자/
    // 수정 메서드를 새로 붙였다. role/problem/solution/result는 STAR(상황-역할/과제-해결-결과)
    // 구조를 그대로 필드로 나눈 것 - ai-server 쪽 질문식 작성(태스크 #60)도 이 4개에 맞춰
    // 질문을 던진다.
    public Project(Long memberId, String title, String roleDescription, String problemDescription,
                    String solutionDescription, String resultDescription, String githubUrl,
                    String deploymentUrl, LocalDate startedAt, LocalDate endedAt) {
        this.memberId = memberId;
        this.title = title;
        this.roleDescription = roleDescription;
        this.problemDescription = problemDescription;
        this.solutionDescription = solutionDescription;
        this.resultDescription = resultDescription;
        this.githubUrl = githubUrl;
        this.deploymentUrl = deploymentUrl;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }
    public void update(String title, String roleDescription, String problemDescription,
                        String solutionDescription, String resultDescription, String githubUrl,
                        String deploymentUrl, LocalDate startedAt, LocalDate endedAt) {
        this.title = title;
        this.roleDescription = roleDescription;
        this.problemDescription = problemDescription;
        this.solutionDescription = solutionDescription;
        this.resultDescription = resultDescription;
        this.githubUrl = githubUrl;
        this.deploymentUrl = deploymentUrl;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }
    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getTitle() { return title; }
    public String getRoleDescription() { return roleDescription; }
    public String getProblemDescription() { return problemDescription; }
    public String getSolutionDescription() { return solutionDescription; }
    public String getResultDescription() { return resultDescription; }
    public String getGithubUrl() { return githubUrl; }
    public String getDeploymentUrl() { return deploymentUrl; }
    public LocalDate getStartedAt() { return startedAt; }
    public LocalDate getEndedAt() { return endedAt; }
}

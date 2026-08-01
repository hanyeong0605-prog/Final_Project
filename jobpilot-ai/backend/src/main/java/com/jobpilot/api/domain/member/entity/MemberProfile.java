package com.jobpilot.api.domain.member.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.LocalDate;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "member_profiles")
public class MemberProfile {
    @Id @Column(name = "member_id") private Long memberId;
    @Column(name = "target_role", nullable = false) private String targetRole;
    @Column(name = "target_job_family", nullable = false) private String targetJobFamily;
    @Column(name = "preferred_locations", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON) private JsonNode preferredLocations;
    @Column(name = "available_from") private LocalDate availableFrom;
    @Column(name = "experience_type", nullable = false) private String experienceType;
    @Column(name = "github_username") private String githubUsername;
    protected MemberProfile() {}
    public MemberProfile(Long memberId) { this.memberId = memberId; }
    public void update(String targetRole, String targetJobFamily, ArrayNode preferredLocations,
                       LocalDate availableFrom, String experienceType, String githubUsername) {
        this.targetRole = targetRole; this.targetJobFamily = targetJobFamily;
        this.preferredLocations = preferredLocations; this.availableFrom = availableFrom;
        this.experienceType = experienceType; this.githubUsername = githubUsername;
    }
    public Long getMemberId() { return memberId; }
    public String getTargetRole() { return targetRole; }
    public String getTargetJobFamily() { return targetJobFamily; }
    public JsonNode getPreferredLocations() { return preferredLocations; }
    public LocalDate getAvailableFrom() { return availableFrom; }
    public String getExperienceType() { return experienceType; }
    public String getGithubUsername() { return githubUsername; }
}

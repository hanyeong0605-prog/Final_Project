package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "skill_aliases")
public class SkillAlias {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "skill_id", nullable = false) private Long skillId;
    @Column(nullable = false) private String alias;
    @Column(name = "normalized_alias", nullable = false) private String normalizedAlias;

    protected SkillAlias() {}

    public Long getSkillId() { return skillId; }
    public String getNormalizedAlias() { return normalizedAlias; }
}

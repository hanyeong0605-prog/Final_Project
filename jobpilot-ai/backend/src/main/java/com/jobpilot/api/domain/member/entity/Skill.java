package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "skills")
public class Skill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String name;
    @Column(nullable = false) private String category;
    @Column(name = "catalog_status", nullable = false) private String catalogStatus = "RAW";
    @Column(name = "parent_skill_id") private Long parentSkillId;
    @Column(name = "display_order", nullable = false) private int displayOrder = 9999;
    @Column(name = "normalized_name") private String normalizedName;
    protected Skill() {}

    public Skill(String name, String category) {
        this.name = name;
        this.category = category;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getCatalogStatus() { return catalogStatus; }
    public Long getParentSkillId() { return parentSkillId; }
    public int getDisplayOrder() { return displayOrder; }
    public String getNormalizedName() { return normalizedName; }
    public boolean isCanonical() { return "CANONICAL".equals(catalogStatus); }
}

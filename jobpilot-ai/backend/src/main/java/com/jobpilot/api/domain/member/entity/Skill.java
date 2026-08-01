package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "skills")
public class Skill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String name;
    @Column(nullable = false) private String category;
    protected Skill() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
}

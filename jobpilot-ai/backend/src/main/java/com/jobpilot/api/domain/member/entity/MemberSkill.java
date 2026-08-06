package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.*;

//브뤤취

@Entity
@Table(name = "member_skills")
public class MemberSkill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "skill_id", nullable = false) private Long skillId;
    @Column(name = "self_reported_level") private String selfReportedLevel;
    private String note;
    protected MemberSkill() {}

    public MemberSkill(Long memberId, Long skillId, String selfReportedLevel, String note) {
        this.memberId = memberId;
        this.skillId = skillId;
        this.selfReportedLevel = selfReportedLevel;
        this.note = note;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public Long getSkillId() { return skillId; }
    public String getSelfReportedLevel() { return selfReportedLevel; }
    public String getNote() { return note; }
}

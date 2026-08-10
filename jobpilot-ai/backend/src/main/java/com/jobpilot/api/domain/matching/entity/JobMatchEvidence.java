package com.jobpilot.api.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_match_evidences")
public class JobMatchEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_match_id")
    private Long jobMatchId;

    @Column(name = "job_requirement_id")
    private Long jobRequirementId;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(name = "member_evidence_type")
    private String memberEvidenceType;

    @Column(name = "member_evidence_id")
    private Long memberEvidenceId;

    private String status;
    @jakarta.persistence.Lob
    @Column(columnDefinition = "TEXT")
    private String comment;

    @jakarta.persistence.Lob
    @Column(name = "gap_action", columnDefinition = "TEXT")
    private String gapAction;

    protected JobMatchEvidence() {}

    public JobMatchEvidence(Long jobMatchId, Long jobRequirementId, Long skillId,
                            String memberEvidenceType, Long memberEvidenceId, String status,
                            String comment, String gapAction) {
        this.jobMatchId = jobMatchId;
        this.jobRequirementId = jobRequirementId;
        this.skillId = skillId;
        this.memberEvidenceType = memberEvidenceType;
        this.memberEvidenceId = memberEvidenceId;
        this.status = status;
        this.comment = comment;
        this.gapAction = gapAction;
    }

    public Long getId() { return id; }
    public Long getJobMatchId() { return jobMatchId; }
    public Long getJobRequirementId() { return jobRequirementId; }
    public Long getSkillId() { return skillId; }
    public String getMemberEvidenceType() { return memberEvidenceType; }
    public Long getMemberEvidenceId() { return memberEvidenceId; }
    public String getStatus() { return status; }
    public String getComment() { return comment; }
    public String getGapAction() { return gapAction; }
}

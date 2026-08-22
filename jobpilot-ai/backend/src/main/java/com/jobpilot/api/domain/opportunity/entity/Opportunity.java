package com.jobpilot.api.domain.opportunity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "opportunities")
public class Opportunity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String type;
    @Column(name = "source_name", nullable = false) private String sourceName;
    @Column(name = "external_id") private String externalId;
    @Column(nullable = false) private String title;
    private String organization;
    @Lob @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "source_url", nullable = false, length = 1500) private String sourceUrl;
    @Column(name = "application_start_at") private LocalDateTime applicationStartAt;
    @Column(name = "deadline_at") private LocalDateTime deadlineAt;
    @Column(name = "event_start_at") private LocalDateTime eventStartAt;
    @Column(name = "event_end_at") private LocalDateTime eventEndAt;
    @Column(nullable = false) private String status;
    @Column(name="training_address") private String trainingAddress; @Column(name="training_phone") private String trainingPhone; @Column(name="training_target") private String trainingTarget; private Integer capacity; @Column(name="enrolled_count") private Integer enrolledCount; @Column(name="course_fee") private Integer courseFee; @Column(name="self_pay_fee") private Integer selfPayFee; @Column(name="satisfaction_score") private java.math.BigDecimal satisfactionScore; @Column(name="detail_url") private String detailUrl; @Column(name="institution_url") private String institutionUrl;
    @Column(name="training_ncs_code") private String trainingNcsCode; @Lob @Column(name="training_contents", columnDefinition="TEXT") private String trainingContents; @Column(name="training_certificate") private String trainingCertificate; @Column(name="training_grade") private String trainingGrade; @Column(name="employment_rate_3m") private String employmentRate3m; @Column(name="employment_rate_6m") private String employmentRate6m; @Column(name="thumbnail_url") private String thumbnailUrl;

    protected Opportunity() {}

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getOrganization() { return organization; }
    public String getDescription() { return description; }
    public String getSourceUrl() { return sourceUrl; }
    public LocalDateTime getEventStartAt() { return eventStartAt; }
    public LocalDateTime getEventEndAt() { return eventEndAt; }
    public String getStatus() { return status; }
    public String getTrainingAddress(){return trainingAddress;} public String getTrainingPhone(){return trainingPhone;} public String getTrainingTarget(){return trainingTarget;} public Integer getCapacity(){return capacity;} public Integer getEnrolledCount(){return enrolledCount;} public Integer getCourseFee(){return courseFee;} public Integer getSelfPayFee(){return selfPayFee;} public java.math.BigDecimal getSatisfactionScore(){return satisfactionScore;} public String getDetailUrl(){return detailUrl;} public String getInstitutionUrl(){return institutionUrl;}
    public String getTrainingNcsCode(){return trainingNcsCode;} public String getTrainingContents(){return trainingContents;} public String getTrainingCertificate(){return trainingCertificate;} public String getTrainingGrade(){return trainingGrade;} public String getEmploymentRate3m(){return employmentRate3m;} public String getEmploymentRate6m(){return employmentRate6m;} public String getThumbnailUrl(){return thumbnailUrl;}
    public LocalDateTime getApplicationStartAt() { return applicationStartAt; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
}

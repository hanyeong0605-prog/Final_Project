package com.jobpilot.api.domain.jobposting.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job_postings")
public class JobPosting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_job_id", nullable = false, length = 150)
    private String externalJobId;

    @Column(name = "source_provider", nullable = false, length = 30)
    private String sourceProvider;

    @Column(name = "source_company_id", length = 150)
    private String sourceCompanyId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_url", length = 1500)
    private String companyUrl;

    @Column(name = "company_logo_url", length = 1500)
    private String companyLogoUrl;

    private String title;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String description;

    @Column(name = "source_url", nullable = false, length = 1500)
    private String sourceUrl;

    private String location;

    @Column(name = "employment_type")
    private String employmentType;

    @Column(name = "experience_type")
    private String experienceType;

    @Column(name = "is_entry_level")
    private Boolean entryLevel;

    @Column(name = "industry_code", length = 100) private String industryCode;
    @Column(name = "industry_name") private String industryName;
    @Column(name = "job_mid_code", length = 100) private String jobMidCode;
    @Column(name = "job_mid_name") private String jobMidName;
    @Column(name = "job_code", length = 500) private String jobCode;
    @Column(name = "job_name", length = 1000) private String jobName;
    private String salary;
    @Lob @Column(columnDefinition = "TEXT") private String keywords;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Column(name = "is_rolling_deadline", nullable = false)
    private boolean rollingDeadline;

    @Column(nullable = false)
    private String status;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;

    @Column(name = "crawl_status", nullable = false)
    private String crawlStatus;

    @Column(name = "crawled_at")
    private LocalDateTime crawledAt;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "raw_payload", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode rawPayload;

    protected JobPosting() {}

    public JobPosting(String externalJobId) {
        this("WANTED", externalJobId);
    }

    public JobPosting(String sourceProvider, String externalJobId) {
        this.sourceProvider = sourceProvider;
        this.externalJobId = externalJobId;
    }

    public void updateFromProvider(String title, String companyName, String companyUrl, String description,
                                   String sourceUrl, String location, String employmentType,
                                   String experienceType, String industryCode, String industryName,
                                   String jobMidCode, String jobMidName, String jobCode, String jobName,
                                   String salary, String keywords, LocalDateTime publishedAt,
                                   LocalDateTime deadlineAt, boolean rollingDeadline,
                                   String status, LocalDateTime fetchedAt,
                                   LocalDateTime sourceUpdatedAt, String crawlStatus,
                                   LocalDateTime crawledAt, JsonNode rawPayload) {
        this.title = title;
        this.companyName = companyName;
        this.companyUrl = companyUrl;
        this.description = description;
        this.sourceUrl = sourceUrl;
        this.location = location;
        this.employmentType = employmentType;
        this.experienceType = experienceType;
        this.industryCode = industryCode;
        this.industryName = industryName;
        this.jobMidCode = jobMidCode;
        this.jobMidName = jobMidName;
        this.jobCode = jobCode;
        this.jobName = jobName;
        this.salary = salary;
        this.keywords = keywords;
        this.publishedAt = publishedAt;
        this.deadlineAt = deadlineAt;
        this.rollingDeadline = rollingDeadline;
        this.status = status;
        this.fetchedAt = fetchedAt;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.crawlStatus = crawlStatus;
        this.crawledAt = crawledAt;
        this.rawPayload = rawPayload;
    }

    public Long getId() { return id; }
    public String getExternalJobId() { return externalJobId; }
    public String getSourceProvider() { return sourceProvider; }
    public String getSourceCompanyId() { return sourceCompanyId; }
    public String getCompanyName() { return companyName; }
    public String getCompanyUrl() { return companyUrl; }
    public String getCompanyLogoUrl() { return companyLogoUrl; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSourceUrl() { return sourceUrl; }
    public String getLocation() { return location; }
    public String getEmploymentType() { return employmentType; }
    public String getExperienceType() { return experienceType; }
    public Boolean getEntryLevel() { return entryLevel; }
    public String getIndustryName() { return industryName; }
    public String getJobMidName() { return jobMidName; }
    public String getJobName() { return jobName; }
    public String getSalary() { return salary; }
    public String getKeywords() { return keywords; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public boolean isRollingDeadline() { return rollingDeadline; }
    public String getStatus() { return status; }
    public String getCrawlStatus() { return crawlStatus; }
    public long getViewCount() { return viewCount; }
    public JsonNode getRawPayload() { return rawPayload; }
}

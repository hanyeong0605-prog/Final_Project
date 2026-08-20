package com.jobpilot.api.domain.jobposting.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
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

    // 2026-08-19: 기업회원이 직접 등록한 공고만 값이 있다(크롤링 공고는 NULL) -
    // sourceProvider="EMPLOYER"일 때만 채워진다. V32__employer_accounts.sql 참고.
    @Column(name = "employer_account_id")
    private Long employerAccountId;

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
    public void changeStatus(String status) { this.status = status; }

    public void updateByAdmin(String title, String companyName, String location, LocalDateTime deadlineAt, String status) {
        this.title = title;
        this.companyName = companyName;
        this.location = location;
        this.deadlineAt = deadlineAt;
        this.status = status;
    }

    public Long getEmployerAccountId() { return employerAccountId; }
    public boolean isEmployerPosting() { return employerAccountId != null; }

    /** 저장 후 생성된 id로 우리 사이트 상세 페이지 URL을 채워 넣을 때 쓴다(기업 자체 등록 공고 전용). */
    public void assignInternalSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    /**
     * 2026-08-19: 기업회원이 직접 등록하는 공고 - 크롤링 공고와 같은 job_postings
     * 테이블/컬럼을 그대로 쓰되(사용자 요청: "크롤링 한 채용 테이블 컬럼과 동일하게"),
     * sourceProvider="EMPLOYER" + externalJobId="EMP-{UUID}"로 구분한다. 이렇게 하면
     * 기존 (source_provider, external_job_id) 유니크 제약과 크롤러 upsert 경로를
     * 하나도 안 건드리고 그대로 재사용할 수 있다.
     */
    public static JobPosting createByEmployer(Long employerAccountId, String title, String companyName, String companyUrl,
                                                String description, String location, String employmentType,
                                                String experienceType, String salary, LocalDateTime deadlineAt,
                                                boolean rollingDeadline) {
        JobPosting posting = new JobPosting("EMPLOYER", "EMP-" + UUID.randomUUID());
        posting.employerAccountId = employerAccountId;
        posting.applyEmployerFields(title, companyName, companyUrl, description, location, employmentType,
                experienceType, salary, deadlineAt, rollingDeadline);
        LocalDateTime now = LocalDateTime.now();
        posting.status = "ACTIVE";
        posting.publishedAt = now;
        posting.fetchedAt = now;
        posting.crawlStatus = "MANUAL";
        posting.sourceUrl = "";
        return posting;
    }

    public void updateByEmployer(String title, String companyName, String companyUrl, String description, String location,
                                  String employmentType, String experienceType, String salary, LocalDateTime deadlineAt,
                                  boolean rollingDeadline) {
        applyEmployerFields(title, companyName, companyUrl, description, location, employmentType, experienceType,
                salary, deadlineAt, rollingDeadline);
    }

    private void applyEmployerFields(String title, String companyName, String companyUrl, String description, String location,
                                      String employmentType, String experienceType, String salary, LocalDateTime deadlineAt,
                                      boolean rollingDeadline) {
        this.title = title;
        this.companyName = companyName;
        this.companyUrl = companyUrl;
        this.description = description;
        this.location = location;
        this.employmentType = employmentType;
        this.experienceType = experienceType;
        this.salary = salary;
        this.deadlineAt = deadlineAt;
        this.rollingDeadline = rollingDeadline;
    }
}

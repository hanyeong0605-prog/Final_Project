package com.jobpilot.api.domain.locationjobs.matching.dto;

public class LocationJobResponseDto {
    private Long id;
    private Long jobPostingId;
    private String title;
    private String companyName;
    private String companyLogoUrl;
    private String address;
    private String experienceType;
    private String employmentType;
    private String deadlineAt;
    private String keywords;
    private String salary;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;

    public LocationJobResponseDto() {}

    public LocationJobResponseDto(Long id, Long jobPostingId, String title, String companyName,
                                  String companyLogoUrl, String address, String experienceType,
                                  String employmentType, String deadlineAt, String keywords,
                                  String salary, Double latitude, Double longitude, Double distanceKm) {
        this.id = id;
        this.jobPostingId = jobPostingId;
        this.title = title;
        this.companyName = companyName;
        this.companyLogoUrl = companyLogoUrl;
        this.address = address;
        this.experienceType = experienceType;
        this.employmentType = employmentType;
        this.deadlineAt = deadlineAt;
        this.keywords = keywords;
        this.salary = salary;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceKm = distanceKm;
    }

    public static class Builder {
        private Long id;
        private Long jobPostingId;
        private String title;
        private String companyName;
        private String companyLogoUrl;
        private String address;
        private String experienceType;
        private String employmentType;
        private String deadlineAt;
        private String keywords;
        private String salary;
        private Double latitude;
        private Double longitude;
        private Double distanceKm;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder jobPostingId(Long jobPostingId) { this.jobPostingId = jobPostingId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder companyName(String companyName) { this.companyName = companyName; return this; }
        public Builder companyLogoUrl(String companyLogoUrl) { this.companyLogoUrl = companyLogoUrl; return this; }
        public Builder address(String address) { this.address = address; return this; }
        public Builder experienceType(String experienceType) { this.experienceType = experienceType; return this; }
        public Builder employmentType(String employmentType) { this.employmentType = employmentType; return this; }
        public Builder deadlineAt(String deadlineAt) { this.deadlineAt = deadlineAt; return this; }
        public Builder keywords(String keywords) { this.keywords = keywords; return this; }
        public Builder salary(String salary) { this.salary = salary; return this; }
        public Builder latitude(Double latitude) { this.latitude = latitude; return this; }
        public Builder longitude(Double longitude) { this.longitude = longitude; return this; }
        public Builder distanceKm(Double distanceKm) { this.distanceKm = distanceKm; return this; }

        public LocationJobResponseDto build() {
            return new LocationJobResponseDto(
                    id, jobPostingId, title, companyName, companyLogoUrl,
                    address, experienceType, employmentType, deadlineAt,
                    keywords, salary, latitude, longitude, distanceKm
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public Long getId() { return id; }
    public Long getJobPostingId() { return jobPostingId; }
    public String getTitle() { return title; }
    public String getCompanyName() { return companyName; }
    public String getCompanyLogoUrl() { return companyLogoUrl; }
    public String getAddress() { return address; }
    public String getExperienceType() { return experienceType; }
    public String getEmploymentType() { return employmentType; }
    public String getDeadlineAt() { return deadlineAt; }
    public String getKeywords() { return keywords; }
    public String getSalary() { return salary; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getDistanceKm() { return distanceKm; }
}
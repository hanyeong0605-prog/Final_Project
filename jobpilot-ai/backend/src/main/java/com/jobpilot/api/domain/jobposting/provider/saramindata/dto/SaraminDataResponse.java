package com.jobpilot.api.domain.jobposting.provider.saramindata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class SaraminDataResponse {
    private SaraminDataResponse() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Root(Jobs jobs, Integer code, String message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Jobs(int count, int start, String total, List<Job> job) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Job(
            String id,
            String url,
            int active,
            Company company,
            Position position,
            String keyword,
            CodeName salary,
            @JsonProperty("posting-timestamp") String postingTimestamp,
            @JsonProperty("modification-timestamp") String modificationTimestamp,
            @JsonProperty("opening-timestamp") String openingTimestamp,
            @JsonProperty("expiration-timestamp") String expirationTimestamp,
            @JsonProperty("close-type") CodeName closeType
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Company(CompanyDetail detail) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompanyDetail(String href, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Position(
            String title,
            CodeName location,
            CodeName industry,
            @JsonProperty("job-type") CodeName jobType,
            @JsonProperty("job-mid-code") CodeName jobMidCode,
            @JsonProperty("job-code") CodeName jobCode,
            @JsonProperty("experience-level") ExperienceLevel experienceLevel,
            @JsonProperty("required-education-level") CodeName requiredEducationLevel
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodeName(String code, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExperienceLevel(String code, Integer min, Integer max, String name) {}
}

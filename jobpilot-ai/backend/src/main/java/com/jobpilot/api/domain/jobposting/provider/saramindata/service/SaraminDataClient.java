package com.jobpilot.api.domain.jobposting.provider.saramindata.service;

import com.jobpilot.api.domain.jobposting.provider.saramindata.config.SaraminDataProperties;
import com.jobpilot.api.domain.jobposting.provider.saramindata.dto.SaraminDataResponse.Root;
import com.jobpilot.api.domain.jobposting.provider.saramindata.exception.SaraminDataException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SaraminDataClient {
    private final RestClient restClient;
    private final SaraminDataProperties properties;

    public SaraminDataClient(@Qualifier("saraminDataRestClient") RestClient restClient,
                             SaraminDataProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public Root fetchPage(int start) {
        try {
            Root root = restClient.get()
                    .uri(properties.baseUrl(), builder -> builder
                            .queryParam("access-key", properties.accessKey())
                            .queryParam("count", properties.countPerPage())
                            .queryParam("job_mid_cd", properties.jobMidCode())
                            .queryParam("job_type", properties.jobType())
                            .queryParam("sort", "pd")
                            .queryParam("start", start)
                            .build())
                    .retrieve()
                    .body(Root.class);
            if (root == null || root.jobs() == null) {
                String reason = root != null && root.message() != null ? root.message() : "jobs가 없습니다.";
                throw new SaraminDataException("사람인 API 오류: " + reason);
            }
            return root;
        } catch (SaraminDataException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SaraminDataException("사람인 API 호출에 실패했습니다.", exception);
        }
    }
}

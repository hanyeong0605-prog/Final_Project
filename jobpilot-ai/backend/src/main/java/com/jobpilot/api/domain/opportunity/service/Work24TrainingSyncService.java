package com.jobpilot.api.domain.opportunity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Imports only information-communication training courses; it never stores the full Work24 catalogue. */
@Service
public class Work24TrainingSyncService {
    private static final String ENDPOINT = "https://www.work24.go.kr/cm/openApi/call/hr/callOpenApiSvcInfo310L01.do";
    private static final List<String> IT_WORDS = List.of("개발", "프로그래밍", "코딩", "소프트웨어", "웹", "앱", "java", "spring", "python", "react", "데이터", "database", "db", "ai", "인공지능", "클라우드", "aws", "docker", "보안", "네트워크", "linux", "빅데이터");
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final RestClient client = RestClient.create();
    private final boolean enabled; private final String apiKey;
    public Work24TrainingSyncService(JdbcTemplate jdbc, ObjectMapper json, @Value("${work24.enabled:false}") boolean enabled, @Value("${work24.nae-il-learning-api-key:}") String apiKey) { this.jdbc=jdbc; this.json=json; this.enabled=enabled; this.apiKey=apiKey; }
    @Scheduled(cron = "${work24.sync-cron:0 15 5 * * *}", zone = "Asia/Seoul")
    public void scheduledSync() { if (enabled && !apiKey.isBlank()) sync(); }
    public int sync() {
        try {
            LocalDate today=LocalDate.now(); String body=client.get().uri(uri -> uri.scheme("https").host("www.work24.go.kr").path("/cm/openApi/call/hr/callOpenApiSvcInfo310L01.do")
                    .queryParam("authKey", apiKey).queryParam("returnType", "JSON").queryParam("outType", "1").queryParam("pageNum", 1).queryParam("pageSize", 100)
                    .queryParam("srchTraStDt", today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)).queryParam("srchTraEndDt", today.plusMonths(6).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
                    .queryParam("srchNcs1", "20").queryParam("sort", "ASC").queryParam("sortCol", "2").build()).retrieve().body(String.class);
            JsonNode courses=json.readTree(body).path("srchList"); int saved=0; if (!courses.isArray()) return 0;
            for (JsonNode course : courses) if (isDevelopmentCourse(course)) { upsert(course, tags(course)); saved++; }
            return saved;
        } catch (Exception ignored) { return 0; }
    }
    private boolean isDevelopmentCourse(JsonNode course) { String ncs=course.path("ncsCd").asText(); String title=course.path("title").asText("").toLowerCase(Locale.ROOT); return ncs.startsWith("20") && IT_WORDS.stream().anyMatch(title::contains); }
    private List<String> tags(JsonNode course) { String text=(course.path("title").asText()+" "+course.path("contents").asText()).toLowerCase(Locale.ROOT); List<String> found=new ArrayList<>(); for (String word:IT_WORDS) if(text.contains(word)) found.add(word); return found; }
    private void upsert(JsonNode c, List<String> tags) {
        String id=c.path("trprId").asText(); if(id.isBlank()) return; LocalDateTime start=date(c.path("traStartDate").asText()), end=date(c.path("traEndDate").asText());
        String description="WORK24_IT_TAGS="+String.join(",", tags)+"; NCS="+c.path("ncsCd").asText();
        jdbc.update("INSERT INTO opportunities(type,source_name,external_id,title,organization,description,source_url,event_start_at,event_end_at,status) VALUES ('교육','WORK24',?,?,?,?,?,?,?,'ACTIVE') ON DUPLICATE KEY UPDATE title=VALUES(title),organization=VALUES(organization),description=VALUES(description),source_url=VALUES(source_url),event_start_at=VALUES(event_start_at),event_end_at=VALUES(event_end_at),status='ACTIVE'", id,c.path("title").asText(),c.path("subTitle").asText(),description,c.path("titleLink").asText(),start,end);
    }
    private LocalDateTime date(String value) { try { return LocalDate.parse(value).atStartOfDay(); } catch(Exception ignored) { return null; } }
}

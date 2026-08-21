package com.jobpilot.api.domain.opportunity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Imports only information-communication training courses; it never stores the full Work24 catalogue. */
@Service
public class Work24TrainingSyncService {
    private static final Logger log = LoggerFactory.getLogger(Work24TrainingSyncService.class);
    private static final String ENDPOINT = "https://www.work24.go.kr/cm/openApi/call/hr/callOpenApiSvcInfo310L01.do";
    private static final List<String> IT_WORDS = List.of("개발", "프로그래밍", "코딩", "소프트웨어", "웹", "앱", "java", "spring", "python", "react", "데이터", "database", "db", "ai", "인공지능", "클라우드", "aws", "docker", "보안", "네트워크", "linux", "빅데이터");
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final RestClient client = RestClient.create();
    private final boolean enabled; private final String apiKey;
    public Work24TrainingSyncService(JdbcTemplate jdbc, ObjectMapper json, @Value("${work24.enabled:false}") boolean enabled, @Value("${work24.nae-il-learning-api-key:}") String apiKey) { this.jdbc=jdbc; this.json=json; this.enabled=enabled; this.apiKey=apiKey; }
    @Scheduled(cron = "${work24.sync-cron:0 15 5 * * *}", zone = "Asia/Seoul")
    public void scheduledSync() { if (enabled && !apiKey.isBlank()) sync(); }
    @EventListener(ApplicationReadyEvent.class)
    public void initialSync() { if (enabled && !apiKey.isBlank()) sync(); }
    public int sync() {
        jdbc.update("INSERT INTO work24_training_sync_runs(status) VALUES ('RUNNING')");
        Long runId=jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        try {
            LocalDate today=LocalDate.now(); int saved=0; int total=1;
            for (int page=1; page<=10 && (page-1)*100<total; page++) {
                JsonNode response=json.readTree(fetch(today.minusMonths(3), today.plusMonths(6), page)); JsonNode courses=response.path("srchList"); total=response.path("scn_cnt").asInt(0);
                if (!courses.isArray()) break;
                for (JsonNode course : courses) if (isDevelopmentCourse(course)) { upsert(course, tags(course)); saved++; }
            }
            jdbc.update("UPDATE opportunities SET status='EXPIRED' WHERE source_name='WORK24' AND event_end_at < NOW() AND status='ACTIVE'");
            jdbc.update("UPDATE work24_training_sync_runs SET finished_at=NOW(), imported_count=?, status='SUCCESS' WHERE id=?", saved, runId);
            log.info("고용24 IT·개발 훈련과정 동기화 완료: {}건", saved);
            return saved;
        } catch (Exception error) { jdbc.update("UPDATE work24_training_sync_runs SET finished_at=NOW(), status='FAILED', error_message=? WHERE id=?", error.getMessage(), runId); log.warn("고용24 훈련과정 동기화에 실패했습니다.", error); return 0; }
    }
    private String fetch(LocalDate from, LocalDate to, int page) { return client.get().uri(uri -> uri.scheme("https").host("www.work24.go.kr").path("/cm/openApi/call/hr/callOpenApiSvcInfo310L01.do")
            .queryParam("authKey", apiKey).queryParam("returnType", "JSON").queryParam("outType", "1").queryParam("pageNum", page).queryParam("pageSize", 100)
            .queryParam("srchTraStDt", from.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)).queryParam("srchTraEndDt", to.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
            .queryParam("srchNcs1", "20").queryParam("sort", "ASC").queryParam("sortCol", "2").build()).retrieve().body(String.class); }
    private boolean isDevelopmentCourse(JsonNode course) { String ncs=course.path("ncsCd").asText(); String title=course.path("title").asText("").toLowerCase(Locale.ROOT); return ncs.startsWith("20") && IT_WORDS.stream().anyMatch(title::contains); }
    private List<String> tags(JsonNode course) { String text=(course.path("title").asText()+" "+course.path("contents").asText()).toLowerCase(Locale.ROOT); List<String> found=new ArrayList<>(); for (String word:IT_WORDS) if(text.contains(word)) found.add(word); return found; }
    private void upsert(JsonNode c, List<String> tags) {
        String id=c.path("trprId").asText(); if(id.isBlank()) return; LocalDateTime start=date(c.path("traStartDate").asText()), end=date(c.path("traEndDate").asText());
        String description="WORK24_IT_TAGS="+String.join(",", tags)+"; NCS="+c.path("ncsCd").asText();
        jdbc.update("INSERT INTO opportunities(type,source_name,external_id,title,organization,description,source_url,event_start_at,event_end_at,status,training_address,training_phone,training_target,capacity,enrolled_count,course_fee,self_pay_fee,satisfaction_score,detail_url,institution_url) VALUES ('교육','WORK24',?,?,?,?,?,?,?,'ACTIVE',?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE title=VALUES(title),organization=VALUES(organization),description=VALUES(description),source_url=VALUES(source_url),event_start_at=VALUES(event_start_at),event_end_at=VALUES(event_end_at),training_address=VALUES(training_address),training_phone=VALUES(training_phone),training_target=VALUES(training_target),capacity=VALUES(capacity),enrolled_count=VALUES(enrolled_count),course_fee=VALUES(course_fee),self_pay_fee=VALUES(self_pay_fee),satisfaction_score=VALUES(satisfaction_score),detail_url=VALUES(detail_url),institution_url=VALUES(institution_url),status='ACTIVE'", id,c.path("title").asText(),c.path("subTitle").asText(),description,c.path("titleLink").asText(),start,end,c.path("address").asText(),c.path("telNo").asText(),c.path("trainTarget").asText(),c.path("yardMan").asInt(),c.path("regCourseMan").asInt(),c.path("courseMan").asInt(),c.path("realMan").asInt(),c.path("stdgScor").decimalValue(),c.path("titleLink").asText(),c.path("subTitleLink").asText());
    }
    private LocalDateTime date(String value) { try { return LocalDate.parse(value).atStartOfDay(); } catch(Exception ignored) { return null; } }
}

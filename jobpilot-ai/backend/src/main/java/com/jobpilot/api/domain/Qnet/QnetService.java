package com.jobpilot.api.domain.Qnet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QnetService {

    private final QnetRepository qnetRepository;

    @Value("${data.go.key:}")
    private String serviceKey;

    // 1. 매일 새벽 3시에 큐넷 API를 호출해 DB를 업데이트하는 스케줄러
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void fetchAndSaveQualifications() {
        try {
            // 주의: type=json 파라미터를 추가하거나 응답이 JSON이므로 그에 맞게 처리
            String targetUrl = "http://openapi.q-net.or.kr/api/service/rest/InquiryListNationalQualifcationSVC/getList"
                    + "?serviceKey=" + serviceKey
                    + "&numOfRows=500"
                    + "&pageNo=1";

            System.out.println("요청 URL 확인: " + targetUrl);

            RestTemplate restTemplate = new RestTemplate();
            String jsonResponse = restTemplate.getForObject(URI.create(targetUrl), String.class);

            System.out.println("========== [QNET API RAW RESPONSE] ==========");
            System.out.println(jsonResponse);
            System.out.println("=============================================");

            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                System.err.println("큐넷 API 응답 데이터가 비어 있습니다.");
                return;
            }

            // JSON 파싱 후 엔티티 리스트로 변환
            List<Qnet> parsedList = parseJsonToEntities(jsonResponse);

            if (!parsedList.isEmpty()) {
                qnetRepository.deleteAll();
                qnetRepository.saveAll(parsedList);
                System.out.println("큐넷 자격증 데이터 DB 동기화 완료! (총 " + parsedList.size() + "건)");
            } else {
                System.err.println("파싱된 데이터가 없습니다.");
            }

        } catch (Exception e) {
            System.err.println("큐넷 데이터 동기화 중 에러 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // JSON 문자열을 Qnet 엔티티 리스트로 변환하는 파싱 헬퍼 메서드
    private List<Qnet> parseJsonToEntities(String jsonanc) {
        List<Qnet> list = new ArrayList<>();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonanc);

            // response -> body -> items -> item 경로 탐색
            JsonNode itemNodes = rootNode.path("response").path("body").path("items").path("item");

            if (itemNodes.isArray()) {
                for (JsonNode item : itemNodes) {
                    Qnet qnet = new Qnet();
                    qnet.setQualgbcd(item.path("qualgbcd").asText(""));
                    qnet.setQualgbnm(item.path("qualgbnm").asText(""));
                    qnet.setSeriescd(item.path("seriescd").asInt(0));
                    qnet.setSeriesnm(item.path("seriesnm").asText(""));
                    qnet.setJmcd(item.path("jmcd").asInt(0));
                    qnet.setJmfldnm(item.path("jmfldnm").asText(""));
                    qnet.setObligfldcd(item.path("obligfldcd").asInt(0));
                    qnet.setObligfldnm(item.path("obligfldnm").asText(""));
                    qnet.setMdobligfldcd(item.path("mdobligfldcd").asInt(0));
                    qnet.setMdobligfldnm(item.path("mdobligfldnm").asText(""));

                    list.add(qnet);
                }
            }
        } catch (Exception e) {
            System.err.println("JSON 파싱 중 예외 발생: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    // 2. 프론트엔드에 줄 데이터를 DB에서 조회하여 DTO로 변환
    public List<QnetDto> getQnetFromDB() {
        List<Qnet> qnetList = qnetRepository.findAll();

        return qnetList.stream()
                .map(q -> new QnetDto(
                        q.getQualgbcd(),
                        q.getQualgbnm(),
                        q.getSeriescd(),
                        q.getSeriesnm(),
                        q.getJmcd(),
                        q.getJmfldnm(),
                        q.getObligfldcd(),
                        q.getObligfldnm(),
                        q.getMdobligfldcd(),
                        q.getMdobligfldnm()
                ))
                .collect(Collectors.toList());
    }
}
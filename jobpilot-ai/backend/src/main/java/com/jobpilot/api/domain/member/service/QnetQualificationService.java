package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.member.dto.QnetExamRoundResponse;
import com.jobpilot.api.domain.member.dto.QnetQualificationDetailResponse;
import com.jobpilot.api.domain.member.dto.QnetQualificationResponse;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Q-Net public qualification catalogue. A single cached upstream call prevents typing from spending API quota. */
@Service
public class QnetQualificationService {
    private static final String LIST_URL = "http://openapi.q-net.or.kr/api/service/rest/InquiryListNationalQualifcationSVC/getList";
    // 2026-08-11: 종목코드(jmcd) 기준 상세정보 - InquiryTestInformationNTQSVC 서비스의
    // getJMList(올해 회차별 시험일정)/getFeeList(응시 수수료) 오퍼레이션. 목록 API와 서비스
    // 자체가 다르지만 같은 일반 인증키(qnet.api.key)를 공유해서 쓴다(2026-08-11 data.go.kr
    // 마이페이지에서 세 API 모두 같은 인증키로 승인돼 있는 것 확인함).
    private static final String SCHEDULE_URL = "http://openapi.q-net.or.kr/api/service/rest/InquiryTestInformationNTQSVC/getJMList";
    private static final String FEE_URL = "http://openapi.q-net.or.kr/api/service/rest/InquiryTestInformationNTQSVC/getFeeList";
    private static final int MAX_RESULTS = 20;
    private static final long CACHE_SECONDS = 60 * 60 * 12;
    private static final Pattern ITEM_PATTERN = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();
    private volatile List<QnetQualificationResponse> catalogue = List.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public QnetQualificationService(@Value("${qnet.api.key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public List<QnetQualificationResponse> search(String query) {
        String keyword = requireKeyword(query);
        List<QnetQualificationResponse> all = catalog();
        String normalized = normalize(keyword);
        return all.stream()
                .filter(item -> normalize(item.name()).contains(normalized))
                .sorted(Comparator.comparing((QnetQualificationResponse item) -> normalize(item.name()).startsWith(normalized) ? 0 : 1)
                        .thenComparing(QnetQualificationResponse::name))
                .limit(MAX_RESULTS)
                .toList();
    }

    private List<QnetQualificationResponse> catalog() {
        if (!catalogue.isEmpty() && loadedAt.plusSeconds(CACHE_SECONDS).isAfter(Instant.now())) return catalogue;
        synchronized (this) {
            if (!catalogue.isEmpty() && loadedAt.plusSeconds(CACHE_SECONDS).isAfter(Instant.now())) return catalogue;
            catalogue = requestCatalogue();
            loadedAt = Instant.now();
            return catalogue;
        }
    }

    private List<QnetQualificationResponse> requestCatalogue() {
        if (apiKey.isBlank()) throw new IllegalStateException("Q-Net 자격증 API 키가 설정되지 않았습니다.");
        URI uri = UriComponentsBuilder.fromUriString(LIST_URL)
                // serviceKey is an opaque credential: never decode or transform it before sending.
                .queryParam("serviceKey", apiKey)
                .build().encode().toUri();
        try {
            // Q-Net's gateway does not reliably return the catalogue to bare Java requests.
            // Send explicit browser-like request headers, equivalent to the successful curl check.
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; JobPilot/1.0)");
            headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.ALL));
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String xml = response.getBody();
            if (xml == null || xml.isBlank()) throw new IllegalStateException("Q-Net 자격증 API 응답이 비어 있습니다.");
            List<QnetQualificationResponse> result = new ArrayList<>();
            Matcher items = ITEM_PATTERN.matcher(xml);
            while (items.find()) {
                String item = items.group(1);
                String name = text(item, "jmfldnm");
                if (!name.isBlank()) result.add(new QnetQualificationResponse(text(item, "jmcd"), name,
                        text(item, "qualgbnm"), text(item, "obligfldnm"), text(item, "mdobligfldnm")));
            }
            if (result.isEmpty()) throw new IllegalStateException("Q-Net 자격증 목록을 가져오지 못했습니다.");
            return List.copyOf(result);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Q-Net 자격증 목록을 조회하지 못했습니다.");
        }
    }

    // 2026-08-11: 종목코드(jmcd) 하나에 대한 상세정보 - 올해 시행 회차별 시험일정
    // (getJMList) + 응시 수수료(getFeeList)를 한 번에 묶어서 준다. 두 오퍼레이션 다
    // jmCd 파라미터가 필수라 목록 조회(catalog())처럼 캐싱하지 않고 매 요청마다 부른다
    // (사용자가 "상세보기"를 누른 종목만 조회하므로 트래픽 부담이 적음).
    public QnetQualificationDetailResponse detail(String jmcd) {
        String code = requireJmcd(jmcd);
        List<QnetExamRoundResponse> rounds = requestSchedule(code);
        String fee = requestFee(code);
        String name = rounds.stream().map(QnetExamRoundResponse::roundName)
                .filter(n -> n != null && !n.isBlank()).findFirst().orElse("");
        return new QnetQualificationDetailResponse(code, name, fee, rounds);
    }

    private List<QnetExamRoundResponse> requestSchedule(String jmcd) {
        String xml = fetchDetailXml(SCHEDULE_URL, jmcd);
        List<QnetExamRoundResponse> rounds = new ArrayList<>();
        Matcher items = ITEM_PATTERN.matcher(xml);
        while (items.find()) {
            String item = items.group(1);
            rounds.add(new QnetExamRoundResponse(
                    text(item, "implplannm"),
                    text(item, "docexamstartdt"), text(item, "docexamenddt"), text(item, "docpassdt"),
                    text(item, "pracexamstartdt"), text(item, "pracexamenddt"),
                    text(item, "pracpassstartdt"), text(item, "pracpassenddt")));
        }
        return List.copyOf(rounds);
    }

    private String requestFee(String jmcd) {
        String xml = fetchDetailXml(FEE_URL, jmcd);
        Matcher items = ITEM_PATTERN.matcher(xml);
        // 종목 하나엔 보통 응시 수수료 항목이 1건이라, 첫 건의 contents만 쓴다.
        if (items.find()) return text(items.group(1), "contents");
        return "";
    }

    /** getJMList/getFeeList 공통 호출 - jmCd + serviceKey를 쓰는 두 오퍼레이션 모두 이 헬퍼를 쓴다. */
    private String fetchDetailXml(String url, String jmcd) {
        if (apiKey.isBlank()) throw new IllegalStateException("Q-Net 자격증 API 키가 설정되지 않았습니다.");
        URI uri = UriComponentsBuilder.fromUriString(url)
                .queryParam("jmCd", jmcd)
                // serviceKey is an opaque credential: never decode or transform it before sending.
                .queryParam("serviceKey", apiKey)
                .build().encode().toUri();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; JobPilot/1.0)");
            headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.ALL));
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String xml = response.getBody();
            if (xml == null || xml.isBlank()) throw new IllegalStateException("Q-Net 상세정보 API 응답이 비어 있습니다.");
            return xml;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Q-Net 상세정보를 조회하지 못했습니다.");
        }
    }

    private String requireJmcd(String jmcd) {
        if (jmcd == null || jmcd.isBlank()) throw new IllegalArgumentException("종목코드가 필요합니다.");
        return jmcd.trim();
    }

    private String requireKeyword(String query) {
        if (query == null || query.trim().length() < 2) throw new IllegalArgumentException("두 글자 이상 입력해 검색해 주세요.");
        return query.trim();
    }

    private String text(String item, String tag) {
        Matcher value = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(item);
        return value.find() ? unescape(value.group(1).trim()) : "";
    }

    private String unescape(String value) {
        return value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
    }

    private String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(); }
}

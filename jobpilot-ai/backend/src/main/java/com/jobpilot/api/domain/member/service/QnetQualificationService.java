package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.member.dto.QnetExamRoundResponse;
import com.jobpilot.api.domain.member.dto.QnetFieldCountResponse;
import com.jobpilot.api.domain.member.dto.QnetQualificationDetailResponse;
import com.jobpilot.api.domain.member.dto.QnetQualificationPageResponse;
import com.jobpilot.api.domain.member.dto.QnetQualificationResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(QnetQualificationService.class);

    private final String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();
    private volatile List<QnetQualificationResponse> catalogue = List.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public QnetQualificationService(@Value("${qnet.api.key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    // 2026-08-11: "성장 기회 추천" 자동 추천(CertificateRecommendationService)이 검색어
    // 없이 카탈로그 전체를 훑어야 해서 캐시 접근용으로 공개 - search()와 같은 캐시를 쓴다.
    public List<QnetQualificationResponse> catalogSnapshot() {
        return catalog();
    }

    // 2026-08-11: "성장 기회 추천" 페이지의 "전체 자격증 목록" - 검색어 없이 카탈로그를
    // 이름순으로 페이지 단위로 훑어본다(613건을 한 번에 다 내려주면 무거우니).
    private static final int DEFAULT_PAGE_SIZE = 24;
    private static final int MAX_PAGE_SIZE = 100;
    // 2026-08-11: "분야별 버튼" 필터용 - 처음엔 IT 전용 체크박스(itOnly boolean)였는데,
    // "분야별로 버튼 나눠줄 수 있냐"는 요청으로 임의의 field(NCS 직무분야명, 예:
    // "정보통신"/"건설"/"경영·회계·사무") 하나를 골라 걸러내는 범용 파라미터로 바꿨다.
    // null/빈 문자열이면 필터 없이 전체를 준다.
    public QnetQualificationPageResponse list(int page, int size, String field) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size <= 0 ? DEFAULT_PAGE_SIZE : size, 1), MAX_PAGE_SIZE);
        String trimmedField = field == null ? "" : field.trim();
        List<QnetQualificationResponse> all = catalog().stream()
                .filter(item -> trimmedField.isEmpty() || trimmedField.equals(item.field()))
                .sorted(Comparator.comparing(QnetQualificationResponse::name))
                .toList();
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new QnetQualificationPageResponse(all.subList(from, to), to < all.size());
    }

    // 2026-08-11: "전체 자격증 목록" 위에 분야별 필터 버튼을 뿌리기 위한 목록 - 카탈로그에
    // 실제로 존재하는 field 값만(추측 없이) 건수와 함께 내려준다. 건수 내림차순으로 정렬해서
    // 종목이 많은 분야(정보통신 등)가 앞쪽 버튼에 오게 한다.
    public List<QnetFieldCountResponse> fields() {
        return catalog().stream()
                .filter(item -> !item.field().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(QnetQualificationResponse::field, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new QnetFieldCountResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(QnetFieldCountResponse::count).reversed()
                        .thenComparing(QnetFieldCountResponse::field))
                .toList();
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
                // 2026-08-11: 활용가이드(DV_0701)엔 numOfRows/pageNo가 안 나와 있지만, 안 주면
                // Q-Net 게이트웨이가 기본 페이지 크기(소수 건)만 돌려줘서 "정보처리기사" 같은
                // 흔한 종목도 캐시에서 누락되는 버그가 있었다(전체를 한 번에 캐싱하는 구조라
                // 페이징 자체가 필요 없으므로 넉넉히 크게 요청).
                .queryParam("numOfRows", "9999")
                .queryParam("pageNo", "1")
                .build().encode().toUri();
        try {
            String xml = httpGetUtf8(uri);
            log.info("Q-Net catalog raw response length={} preview={}", xml == null ? -1 : xml.length(),
                    xml == null ? "null" : xml.substring(0, Math.min(500, xml.length())));
            if (xml == null || xml.isBlank()) throw new IllegalStateException("Q-Net 자격증 API 응답이 비어 있습니다.");
            List<QnetQualificationResponse> result = new ArrayList<>();
            Matcher items = ITEM_PATTERN.matcher(xml);
            while (items.find()) {
                String item = items.group(1);
                String name = text(item, "jmfldnm");
                if (!name.isBlank()) result.add(new QnetQualificationResponse(text(item, "jmcd"), name,
                        text(item, "qualgbnm"), text(item, "obligfldnm"), text(item, "mdobligfldnm")));
            }
            log.info("Q-Net catalog parsed {} qualification(s)", result.size());
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
            String xml = httpGetUtf8(uri);
            if (xml == null || xml.isBlank()) throw new IllegalStateException("Q-Net 상세정보 API 응답이 비어 있습니다.");
            return xml;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Q-Net 상세정보를 조회하지 못했습니다.");
        }
    }

    // 2026-08-11: 검색이 계속 빈 결과만 나오던 진짜 원인 - Q-Net 응답 Content-Type에
    // charset=UTF-8이 명시돼 있지 않아서 RestTemplate 기본 StringHttpMessageConverter가
    // ISO-8859-1로 디코딩해버림(한글이 "ê°ì¤ê¸°ì ì¬" 식으로 깨짐, 캐싱된 613건은
    // 정상 파싱됐지만 이름이 다 깨져서 어떤 한글 검색어와도 매칭이 안 됐던 것).
    // byte[]로 받아서 UTF-8로 직접 디코딩해 응답 헤더의 (잘못된/누락된) charset을 무시한다.
    private String httpGetUtf8(URI uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; JobPilot/1.0)");
        headers.setAccept(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.ALL));
        ResponseEntity<byte[]> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        byte[] body = response.getBody();
        return body == null ? null : new String(body, StandardCharsets.UTF_8);
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

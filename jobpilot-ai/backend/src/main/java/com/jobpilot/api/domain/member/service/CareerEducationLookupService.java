package com.jobpilot.api.domain.member.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.member.dto.EducationMajorResponse;
import com.jobpilot.api.domain.member.dto.EducationSchoolResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class CareerEducationLookupService {
    private static final String CAREER_OPEN_API = "https://www.career.go.kr/cnet/openapi/getOpenApi";
    private final String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CareerEducationLookupService(@Value("${career.api.key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public List<EducationSchoolResponse> searchSchools(String query, String educationLevel) {
        String keyword = requiredKeyword(query);
        JsonNode body = request("SCHOOL", educationGroup(educationLevel), "searchSchulNm", keyword);
        Map<String, EducationSchoolResponse> result = new LinkedHashMap<>();
        for (JsonNode item : contents(body)) {
            String name = text(item, "schoolName");
            if (name.isBlank()) continue;
            String id = text(item, "seq");
            String campus = text(item, "campusName");
            result.putIfAbsent(id.isBlank() ? name + campus : id,
                    new EducationSchoolResponse(id, name, text(item, "schoolType"), text(item, "region"), campus));
        }
        return List.copyOf(result.values());
    }

    public List<EducationMajorResponse> searchMajors(String query, String educationLevel, String schoolName) {
        String keyword = requiredKeyword(query);
        String selectedSchool = requiredSchool(schoolName);
        JsonNode body = request("MAJOR", educationGroup(educationLevel), "searchTitle", keyword);
        Map<String, EducationMajorResponse> result = new LinkedHashMap<>();
        for (JsonNode item : contents(body).stream().limit(10).toList()) {
            String name = text(item, "mClass");
            if (name.isBlank()) continue;
            String id = text(item, "majorSeq");
            if (id.isBlank() || !isOfferedAt(id, educationLevel, selectedSchool)) continue;
            result.putIfAbsent(id.isBlank() ? name : id,
                    new EducationMajorResponse(id, name, text(item, "lClass"), text(item, "facilName")));
        }
        return List.copyOf(result.values());
    }

    private JsonNode request(String serviceCode, String group, String searchParameter, String keyword) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("커리어넷 교육정보 API 키가 설정되지 않았습니다.");
        }
        // Do not pass an already encoded URL as a String to RestTemplate: it encodes '%' again,
        // turning Korean search text into a different query and returning an empty result set.
        URI uri = UriComponentsBuilder.fromUriString(CAREER_OPEN_API)
                .queryParam("apiKey", apiKey)
                .queryParam("svcType", "api")
                .queryParam("svcCode", serviceCode)
                .queryParam("contentType", "json")
                .queryParam("gubun", group)
                .queryParam("thisPage", 1)
                .queryParam("perPage", 20)
                .queryParam(searchParameter, keyword)
                .build()
                .encode()
                .toUri();
        try {
            String rawResponse = restTemplate.getForObject(uri, String.class);
            if (rawResponse == null || rawResponse.isBlank()) throw new IllegalStateException("커리어넷 교육정보 응답이 비어 있습니다.");
            JsonNode response = objectMapper.readTree(rawResponse);
            JsonNode error = response.path("result").path("content");
            if (!error.isMissingNode() && !text(error, "code").isBlank() && !"0".equals(text(error, "code"))) {
                throw new IllegalStateException("커리어넷 교육정보 조회에 실패했습니다: " + text(error, "message"));
            }
            return response;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("커리어넷 교육정보를 조회할 수 없습니다.");
        }
    }

    private List<JsonNode> contents(JsonNode response) {
        JsonNode contents = response.path("dataSearch").path("content");
        return nodes(contents);
    }

    private List<JsonNode> nodes(JsonNode contents) {
        if (contents.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            contents.forEach(result::add);
            return result;
        }
        return contents.isObject() ? List.of(contents) : List.of();
    }

    private boolean isOfferedAt(String majorId, String educationLevel, String selectedSchool) {
        try {
            JsonNode details = request("MAJOR_VIEW", educationGroup(educationLevel), "majorSeq", majorId);
            // CareerNet returns an object for some majors and an array for others.
            // Checking path("university") directly on an array silently filters every result out.
            return nodes(details.path("dataSearch").path("content")).stream()
                    .flatMap(major -> nodes("HIGH_SCHOOL".equals(educationLevel)
                            ? major.path("setshl") : major.path("university")).stream())
                    .map(item -> text(item, "schoolName"))
                    .anyMatch(name -> sameSchool(name, selectedSchool));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String requiredKeyword(String query) {
        if (query == null || query.trim().length() < 2) {
            throw new IllegalArgumentException("두 글자 이상 입력해 검색해 주세요.");
        }
        return query.trim();
    }

    private String requiredSchool(String schoolName) {
        if (schoolName == null || schoolName.isBlank()) throw new IllegalArgumentException("학교를 먼저 선택해 주세요.");
        return schoolName.trim();
    }

    private String educationGroup(String educationLevel) {
        return "HIGH_SCHOOL".equals(educationLevel) ? "high_list" : "univ_list";
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isMissingNode() || value.isNull() ? "" : value.asText().trim();
    }

    private boolean sameSchool(String first, String second) {
        String a = first.replaceAll("\\s+", "").trim();
        String b = second.replaceAll("\\s+", "").trim();
        return !a.isBlank() && (a.equals(b) || a.contains(b) || b.contains(a));
    }
}

package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.member.dto.QnetQualificationResponse;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Q-Net public qualification catalogue. A single cached upstream call prevents typing from spending API quota. */
@Service
public class QnetQualificationService {
    private static final String LIST_URL = "http://openapi.q-net.or.kr/api/service/rest/InquiryListNationalQualifcationSVC/getList";
    private static final int MAX_RESULTS = 20;
    private static final long CACHE_SECONDS = 60 * 60 * 12;

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
                // The Public Data Portal displays both encoded and decoded keys. Normalize either
                // form before UriComponents encodes it once for the actual HTTP request.
                .queryParam("serviceKey", UriUtils.decode(apiKey, StandardCharsets.UTF_8))
                .queryParam("numOfRows", 1000)
                .build().encode().toUri();
        try {
            String xml = restTemplate.getForObject(uri, String.class);
            if (xml == null || xml.isBlank()) throw new IllegalStateException("Q-Net 자격증 API 응답이 비어 있습니다.");
            // Q-Net occasionally prefixes the XML body with a byte-order mark. DOM rejects that
            // as content before the XML declaration, although curl still displays valid-looking XML.
            int xmlStart = xml.indexOf('<');
            if (xmlStart > 0) xml = xml.substring(xmlStart);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList items = document.getElementsByTagName("item");
            List<QnetQualificationResponse> result = new ArrayList<>();
            for (int index = 0; index < items.getLength(); index++) {
                Element item = (Element) items.item(index);
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

    private String requireKeyword(String query) {
        if (query == null || query.trim().length() < 2) throw new IllegalArgumentException("두 글자 이상 입력해 검색해 주세요.");
        return query.trim();
    }

    private String text(Element parent, String tag) {
        NodeList values = parent.getElementsByTagName(tag);
        return values.getLength() == 0 ? "" : values.item(0).getTextContent().trim();
    }

    private String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(); }
}

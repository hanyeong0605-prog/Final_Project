package com.jobpilot.api.domain.companyfinance.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

@Component
public class OpenDartCorporationZipParser {
    public List<OpenDartCorporation> parse(byte[] zipBytes) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            var entry = zip.getNextEntry();
            if (entry == null) throw new IOException("OpenDART corporation ZIP contains no XML entry");
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(zip);
            var nodes = document.getElementsByTagName("list");
            List<OpenDartCorporation> result = new ArrayList<>();
            for (int index = 0; index < nodes.getLength(); index++) {
                Element element = (Element) nodes.item(index);
                String corpCode = text(element, "corp_code");
                String corpName = text(element, "corp_name");
                if (!corpCode.isBlank() && !corpName.isBlank()) {
                    result.add(new OpenDartCorporation(corpCode, corpName, text(element, "corp_eng_name"),
                            text(element, "stock_code"), text(element, "modify_date")));
                }
            }
            return result;
        }
    }

    private String text(Element element, String name) {
        var nodes = element.getElementsByTagName(name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }
}

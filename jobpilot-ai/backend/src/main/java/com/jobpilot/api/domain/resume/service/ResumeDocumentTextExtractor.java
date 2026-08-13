package com.jobpilot.api.domain.resume.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ResumeDocumentTextExtractor {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 또는 DOCX 파일을 선택해 주세요.");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이력서 파일은 5MB 이하만 업로드할 수 있습니다.");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            byte[] bytes = file.getBytes();
            if (name.endsWith(".pdf")) {
                try (var pdf = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(pdf); }
            }
            if (name.endsWith(".docx")) {
                try (var docx = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                    return docx.getParagraphs().stream().map(p -> p.getText()).collect(Collectors.joining("\n"));
                }
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일의 텍스트를 읽지 못했습니다. 비밀번호가 걸리지 않은 PDF/DOCX인지 확인해 주세요.");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재는 PDF와 DOCX 이력서만 지원합니다.");
    }
}

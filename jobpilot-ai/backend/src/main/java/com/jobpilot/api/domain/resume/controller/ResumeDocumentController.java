package com.jobpilot.api.domain.resume.controller;

import com.jobpilot.api.domain.resume.dto.ResumeDocumentResponse;
import com.jobpilot.api.domain.resume.dto.ResumeDraftRequest;
import com.jobpilot.api.domain.resume.entity.ResumeDocument;
import com.jobpilot.api.domain.resume.service.ResumeDocumentService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/members/me/resume-documents")
public class ResumeDocumentController {
    private final ResumeDocumentService service;
    public ResumeDocumentController(ResumeDocumentService service) { this.service = service; }
    @GetMapping public List<ResumeDocumentResponse> list(Authentication auth) { return service.list(AuthenticatedMember.id(auth)); }
    @GetMapping("/draft-context")
    public Map<String, List<String>> draftContext(Authentication auth) { return service.draftContext(AuthenticatedMember.id(auth)); }
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeDocumentResponse extract(Authentication auth, @RequestPart("file") MultipartFile file) { return service.extract(AuthenticatedMember.id(auth), file); }
    @PostMapping("/{id}/apply-profile")
    public ResumeDocumentResponse apply(Authentication auth, @PathVariable Long id) { return service.applyProfile(AuthenticatedMember.id(auth), id); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        service.delete(AuthenticatedMember.id(auth), id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeDocumentResponse generate(Authentication auth, @RequestPart("request") ResumeDraftRequest request,
            @RequestPart(value = "templateFile", required = false) MultipartFile templateFile) {
        return service.generate(AuthenticatedMember.id(auth), request, templateFile);
    }
    @GetMapping("/{id}/download.docx")
    public ResponseEntity<byte[]> download(Authentication auth, @PathVariable Long id) throws Exception {
        ResumeDocument document = service.owned(AuthenticatedMember.id(auth), id);
        String templateKey = document.getTemplateKey() == null ? "STANDARD" : document.getTemplateKey();
        String resource = switch (templateKey) {
            case "ACADEMY" -> "resume-templates/academy.docx";
            case "SARAMIN" -> "resume-templates/saramin.docx";
            case "JOBKOREA" -> "resume-templates/jobkorea.docx";
            default -> null;
        };
        try (InputStream input = resource == null ? null : new ClassPathResource(resource).getInputStream();
             XWPFDocument docx = input == null ? new XWPFDocument() : new XWPFDocument(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            JsonNode data = document.getStructuredContent() == null ? null : document.getStructuredContent().path("templateData");
            if ("ACADEMY".equals(templateKey) && data != null && !data.isMissingNode()) populateAcademy(docx, data);
            else if (resource == null) appendDraft(docx, document.getGeneratedContent() == null ? document.getExtractedText() : document.getGeneratedContent(), templateKey);
            docx.write(output);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume-" + id + ".docx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).body(output.toByteArray());
        }
    }

    /** The academy form has stable table slots, so populate those slots rather than appending loose text. */
    private static void populateAcademy(XWPFDocument docx, JsonNode data) {
        var tables = docx.getTables(); if (tables.size() < 7) return;
        XWPFTable personal = tables.get(0);
        put(personal, 0, 3, data.path("name").asText()); put(personal, 2, 2, data.path("email").asText());
        put(personal, 3, 2, data.path("phone").asText()); put(personal, 4, 2, data.path("address").asText());
        XWPFTable education = tables.get(1); clearRows(education, 1); put(education, 1, 0, ""); put(education, 1, 1, data.path("schoolName").asText()); put(education, 1, 2, data.path("major").asText()); put(education, 1, 3, join(data.path("educationLevel").asText(), data.path("graduationStatus").asText()));
        XWPFTable training = tables.get(2); clearRows(training, 1); JsonNode trainingEntry = firstEntry(data, "TRAINING"); put(training, 1, 0, period(trainingEntry)); put(training, 1, 1, trainingEntry.path("title").asText()); put(training, 1, 2, trainingEntry.path("content").path("provider").asText());
        XWPFTable project = tables.get(3); clearRows(project, 1); JsonNode firstProject = data.path("projects").isArray() && !data.path("projects").isEmpty() ? data.path("projects").get(0) : null; if (firstProject != null) { put(project, 1, 0, period(firstProject)); put(project, 1, 1, firstProject.path("title").asText()); put(project, 1, 2, firstProject.path("role").asText()); put(project, 1, 3, data.path("skills").asText()); put(project, 2, 3, join(firstProject.path("problem").asText(), firstProject.path("solution").asText(), firstProject.path("result").asText())); }
        XWPFTable skills = tables.get(4); clearRows(skills, 1); put(skills, 1, 1, data.path("skills").asText());
        XWPFTable career = tables.get(5); clearRows(career, 1); JsonNode careerEntry = firstEntry(data, "CAREER"); put(career, 1, 0, period(careerEntry)); put(career, 1, 1, careerEntry.path("title").asText()); put(career, 1, 2, careerEntry.path("content").path("description").asText()); put(career, 1, 3, careerEntry.path("content").path("position").asText());
        XWPFTable certificates = tables.get(6); clearRows(certificates, 1); String[] certificateList = data.path("certificates").asText().split(",\\s*"); for (int index = 0; index < certificateList.length && index + 1 < certificates.getNumberOfRows(); index++) put(certificates, index + 1, 1, certificateList[index]);
    }
    private static void appendDraft(XWPFDocument docx, String content, String templateKey) { for (String line : (content == null ? "" : content).split("\\n")) { var paragraph = docx.createParagraph(); var run = paragraph.createRun(); run.setText(line.replaceFirst("^#+\\s*", "")); if (line.startsWith("#")) { run.setBold(true); run.setFontSize("COMPACT".equals(templateKey) ? 12 : 15); } } }
    private static JsonNode firstEntry(JsonNode data, String type) { if (data == null || !data.path("entries").isArray()) return com.fasterxml.jackson.databind.node.MissingNode.getInstance(); for (JsonNode entry : data.path("entries")) if (type.equals(entry.path("type").asText())) return entry; return com.fasterxml.jackson.databind.node.MissingNode.getInstance(); }
    private static String period(JsonNode entry) { if (entry == null || entry.isMissingNode()) return ""; JsonNode value = entry.path("content"); return join(value.path("startedAt").asText(), value.path("endedAt").asText()); }
    private static void clearRows(XWPFTable table, int fromRow) { for (int row = fromRow; row < table.getNumberOfRows(); row++) for (var cell : table.getRow(row).getTableCells()) cell.setText(""); }
    private static void put(XWPFTable table, int row, int cell, String value) { if (row < table.getNumberOfRows() && cell < table.getRow(row).getTableCells().size()) table.getRow(row).getCell(cell).setText(value == null ? "" : value); }
    private static String join(String... values) { return java.util.Arrays.stream(values).filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(" · ")); }
}

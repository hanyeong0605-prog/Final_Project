package com.jobpilot.api.domain.resume.controller;

import com.jobpilot.api.domain.resume.dto.ResumeDocumentResponse;
import com.jobpilot.api.domain.resume.dto.ResumeDraftRequest;
import com.jobpilot.api.domain.resume.entity.ResumeDocument;
import com.jobpilot.api.domain.resume.service.ResumeDocumentService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.util.Units;
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
    @PatchMapping("/{id}/title")
    public ResumeDocumentResponse rename(Authentication auth, @PathVariable Long id, @RequestBody Map<String, String> request) { return service.rename(AuthenticatedMember.id(auth), id, request.get("title")); }
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
        ClassPathResource template = resource == null ? null : new ClassPathResource(resource);
        try (InputStream input = template == null || !template.exists() ? null : template.getInputStream();
             XWPFDocument docx = input == null ? new XWPFDocument() : new XWPFDocument(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            JsonNode data = service.templateDataForDownload(AuthenticatedMember.id(auth), document);
            if (data != null && !data.isMissingNode()) populateTemplate(docx, data, templateKey);
            else appendDraft(docx, document.getGeneratedContent() == null ? document.getExtractedText() : document.getGeneratedContent(), templateKey);
            docx.write(output);
            String filename = URLEncoder.encode(document.getTitle() + ".docx", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).body(output.toByteArray());
        }
    }

    private static void populateTemplate(XWPFDocument docx, JsonNode data, String templateKey) {
        if ("ACADEMY".equals(templateKey)) { populateAcademy(docx, data); return; }
        List<String> values = List.of(data.path("name").asText(), data.path("email").asText(), data.path("phone").asText(), data.path("address").asText(), data.path("targetRole").asText(), data.path("schoolName").asText(), data.path("major").asText(), data.path("skills").asText(), data.path("certificates").asText(), data.path("technicalSummary").asText());
        int index = 0;
        for (XWPFTable table : docx.getTables()) for (var row : table.getRows()) for (var cell : row.getTableCells()) {
            String current = cell.getText().trim();
            if (index < values.size() && (current.isEmpty() || current.matches(".*(0000|홍길동|OO).*"))) { cell.setText(values.get(index++)); }
        }
        if (!docx.getTables().isEmpty()) insertPhoto(docx, docx.getTables().get(0), 0, 0, data.path("profilePhotoDataUrl").asText());
    }

    /** The academy form has stable table slots, so populate those slots rather than appending loose text. */
    private static void populateAcademy(XWPFDocument docx, JsonNode data) {
        var tables = docx.getTables(); if (tables.size() < 7) return;
        XWPFTable personal = tables.get(0);
        insertPhoto(docx, personal, 0, 0, data.path("profilePhotoDataUrl").asText());
        put(personal, 0, 3, data.path("name").asText()); put(personal, 2, 2, data.path("email").asText());
        put(personal, 0, 5, data.path("hanjaName").asText());
        put(personal, 1, 2, koreanDate(data.path("birthDate").asText()));
        put(personal, 3, 2, data.path("phone").asText()); put(personal, 4, 2, data.path("address").asText());
        XWPFTable education = tables.get(1); clearRows(education, 1);
        JsonNode educationEntry = firstEntry(data, "EDUCATION");
        put(education, 1, 0, period(educationEntry)); put(education, 1, 1, first(educationEntry.path("title").asText(), data.path("schoolName").asText()));
        put(education, 1, 2, first(educationEntry.path("content").path("major").asText(), data.path("major").asText()));
        put(education, 1, 3, first(educationEntry.path("content").path("status").asText(), join(data.path("educationLevel").asText(), data.path("graduationStatus").asText())));

        XWPFTable training = tables.get(2); clearRows(training, 1);
        fillEntries(training, data, "TRAINING", 1, (table, row, entry) -> {
            put(table, row, 0, period(entry)); put(table, row, 1, entry.path("title").asText());
            put(table, row, 2, first(entry.path("content").path("provider").asText(), entry.path("content").path("institution").asText()));
        });

        XWPFTable project = tables.get(3); clearProjectExampleRows(project);
        fillProjectSummary(project, projectAt(data, 0), 1, data.path("skills").asText(), data.path("targetRole").asText());

        XWPFTable skills = tables.get(4);
        fillSkillEnvironment(skills, data.path("skills").asText());

        XWPFTable career = tables.get(5); clearRows(career, 1);
        fillEntries(career, data, "CAREER", 1, (table, row, entry) -> {
            put(table, row, 0, period(entry)); put(table, row, 1, entry.path("title").asText());
            put(table, row, 2, first(entry.path("content").path("description").asText(), entry.path("content").path("duties").asText()));
            put(table, row, 3, entry.path("content").path("position").asText());
        });

        XWPFTable certificates = tables.get(6); clearRows(certificates, 1);
        fillCertificates(certificates, data);
        if (tables.size() < 11) return;

        fillMilitary(tables.get(7), firstEntry(data, "PREFERENCE"));
        fillIntroduction(tables.get(8), data.path("answers"), data.path("selfIntroductions"));
        fillProjectDetail(tables.get(9), projectAt(data, 0), data.path("skills").asText(), data.path("targetRole").asText());
        fillProjectDetail(tables.get(10), projectAt(data, 1), data.path("skills").asText(), data.path("targetRole").asText());
    }
    private interface EntryWriter { void write(XWPFTable table, int row, JsonNode entry); }
    /** Keep the form's field labels but remove the two sample-project value rows. */
    private static void clearProjectExampleRows(XWPFTable table) { for (int row : new int[] {1, 3, 5, 7}) if (row < table.getNumberOfRows()) for (var cell : table.getRow(row).getTableCells()) cell.setText(""); }
    private static void fillEntries(XWPFTable table, JsonNode data, String type, int fromRow, EntryWriter writer) { int row = fromRow; for (JsonNode entry : entries(data, type)) { if (row >= table.getNumberOfRows()) break; writer.write(table, row++, entry); } }
    private static java.util.List<JsonNode> entries(JsonNode data, String type) { java.util.List<JsonNode> result = new java.util.ArrayList<>(); if (data != null && data.path("entries").isArray()) for (JsonNode entry : data.path("entries")) if (type.equals(entry.path("type").asText())) result.add(entry); return result; }
    private static JsonNode projectAt(JsonNode data, int index) { return data.path("projects").isArray() && data.path("projects").size() > index ? data.path("projects").get(index) : com.fasterxml.jackson.databind.node.MissingNode.getInstance(); }
    private static void fillProjectSummary(XWPFTable table, JsonNode project, int row, String skills, String targetRole) { if (project == null || project.isMissingNode()) return; put(table, row, 0, projectPeriod(project)); put(table, row, 1, project.path("title").asText()); put(table, row, 2, first(project.path("role").asText(), targetRole)); put(table, row, 3, skills); put(table, row + 2, 2, join(project.path("problem").asText(), project.path("solution").asText(), project.path("result").asText())); }
    private static void fillCertificates(XWPFTable table, JsonNode data) { JsonNode details = data.path("certificateDetails"); if (details.isArray() && !details.isEmpty()) { for (int index = 0; index < details.size() && index + 1 < table.getNumberOfRows(); index++) { JsonNode certificate = details.get(index); put(table, index + 1, 0, certificate.path("acquiredAt").asText()); put(table, index + 1, 1, certificate.path("name").asText()); put(table, index + 1, 2, certificate.path("issuer").asText()); } return; } String[] certificateList = data.path("certificates").asText().split(",\\s*"); for (int index = 0; index < certificateList.length && index + 1 < table.getNumberOfRows(); index++) put(table, index + 1, 1, certificateList[index]); }
    private static void fillMilitary(XWPFTable table, JsonNode entry) { if (entry == null || entry.isMissingNode()) return; JsonNode content = entry.path("content"); put(table, 1, 0, period(entry)); put(table, 1, 1, join(content.path("branch").asText(), content.path("rank").asText(), content.path("serviceCategory").asText(), content.path("specialty").asText())); put(table, 1, 2, content.path("serviceType").asText()); put(table, 1, 3, content.path("veteranStatus").asText()); }
    private static void fillIntroduction(XWPFTable table, JsonNode answers, JsonNode savedIntroductions) { for (int row = 0; row < 4 && row < table.getNumberOfRows(); row++) { String answer = answers.isArray() && answers.size() > row ? answers.get(row).asText() : ""; String saved = introductionFor(savedIntroductions, row); put(table, row, 1, first(answer, saved)); } }
    private static void fillProjectDetail(XWPFTable table, JsonNode project, String skills, String targetRole) { if (project == null || project.isMissingNode()) return; put(table, 1, 1, project.path("title").asText()); put(table, 2, 1, projectPeriod(project)); put(table, 3, 1, skills); put(table, 4, 1, join(project.path("problem").asText(), project.path("solution").asText())); put(table, 5, 1, first(project.path("role").asText(), targetRole)); put(table, 6, 1, project.path("result").asText()); put(table, 7, 1, join(project.path("githubUrl").asText(), project.path("deploymentUrl").asText())); }
    private static void appendDraft(XWPFDocument docx, String content, String templateKey) { for (String line : (content == null ? "" : content).split("\\n")) { var paragraph = docx.createParagraph(); var run = paragraph.createRun(); run.setText(line.replaceFirst("^#+\\s*", "")); if (line.startsWith("#")) { run.setBold(true); run.setFontSize("COMPACT".equals(templateKey) ? 12 : 15); } } }
    private static JsonNode firstEntry(JsonNode data, String type) { if (data == null || !data.path("entries").isArray()) return com.fasterxml.jackson.databind.node.MissingNode.getInstance(); for (JsonNode entry : data.path("entries")) if (type.equals(entry.path("type").asText())) return entry; return com.fasterxml.jackson.databind.node.MissingNode.getInstance(); }
    private static String period(JsonNode entry) { if (entry == null || entry.isMissingNode()) return ""; JsonNode value = entry.path("content"); return join(value.path("startedAt").asText(), value.path("endedAt").asText()); }
    private static String projectPeriod(JsonNode project) { return join(project.path("startedAt").asText(), project.path("endedAt").asText()); }
    private static String koreanDate(String value) { if (value == null || value.isBlank()) return ""; String[] date = value.split("-"); return date.length == 3 ? date[0] + "년 " + date[1] + "월 " + date[2] + "일" : value; }
    private static String introductionFor(JsonNode introductions, int index) { if (!introductions.isArray()) return ""; String[] terms = {"성장", "잘할", "습득", "회사"}; for (JsonNode item : introductions) if (item.path("title").asText().replaceAll("\\s+", "").contains(terms[index])) return item.path("content").asText(); return introductions.size() > index ? introductions.get(index).path("content").asText() : ""; }
    private static void fillSkillEnvironment(XWPFTable table, String skills) {
        java.util.List<String> values = java.util.Arrays.stream(skills.split(",\\s*")).map(String::trim).filter(value -> !value.isBlank()).toList();
        put(table, 1, 1, selectSkills(values, "java", "python", "javascript", "typescript", "sql", "html", "css", "c++", "c#", "go", "kotlin"));
        put(table, 1, 3, selectSkills(values, "linux", "ubuntu", "windows", "macos"));
        put(table, 2, 3, selectSkills(values, "spring", "react", "vue", "fastapi", "django", "node", "jpa", "hibernate", "mybatis"));
        put(table, 3, 3, selectSkills(values, "nginx", "tomcat", "apache", "iis"));
        put(table, 4, 3, selectSkills(values, "aws", "azure", "gcp", "ec2", "docker", "kubernetes", "compose"));
        put(table, 5, 1, selectSkills(values, "figma", "erd", "cloud", "notion"));
        put(table, 5, 3, selectSkills(values, "mysql", "oracle", "postgres", "mariadb", "mongodb", "redis", "sqlite"));
        put(table, 7, 1, selectSkills(values, "intellij", "vscode", "eclipse", "postman", "git", "github", "jenkins", "github actions", "ci/cd"));
        put(table, 8, 1, selectSkills(values, "git", "github", "svn"));
        java.util.Set<String> categorized = new java.util.HashSet<>(); for (String term : java.util.List.of("java", "python", "javascript", "typescript", "sql", "html", "css", "c++", "c#", "go", "kotlin", "linux", "ubuntu", "windows", "macos", "spring", "react", "vue", "fastapi", "django", "node", "jpa", "hibernate", "mybatis", "nginx", "tomcat", "apache", "iis", "aws", "azure", "gcp", "ec2", "docker", "kubernetes", "compose", "figma", "erd", "cloud", "notion", "mysql", "oracle", "postgres", "mariadb", "mongodb", "redis", "sqlite", "intellij", "vscode", "eclipse", "postman", "git", "github", "jenkins", "github actions", "ci/cd", "svn")) for (String value : values) if (value.toLowerCase(java.util.Locale.ROOT).contains(term)) categorized.add(value); put(table, 9, 1, values.stream().filter(value -> !categorized.contains(value)).collect(java.util.stream.Collectors.joining(", ")));
    }
    private static String selectSkills(java.util.List<String> values, String... terms) { return values.stream().filter(value -> { String lower = value.toLowerCase(java.util.Locale.ROOT); return java.util.Arrays.stream(terms).anyMatch(lower::contains); }).collect(java.util.stream.Collectors.joining(", ")); }
    private static void clearRows(XWPFTable table, int fromRow) { for (int row = fromRow; row < table.getNumberOfRows(); row++) for (var cell : table.getRow(row).getTableCells()) cell.setText(""); }
    private static void put(XWPFTable table, int row, int cell, String value) { if (row < table.getNumberOfRows() && cell < table.getRow(row).getTableCells().size()) table.getRow(row).getCell(cell).setText(value == null ? "" : value); }
    private static void insertPhoto(XWPFDocument docx, XWPFTable table, int row, int cell, String dataUrl) { try { if (dataUrl == null || !dataUrl.startsWith("data:image/")) return; int comma = dataUrl.indexOf(','); if (comma < 0) return; byte[] bytes = java.util.Base64.getDecoder().decode(dataUrl.substring(comma + 1)); var target = table.getRow(row).getCell(cell); target.removeParagraph(0); var run = target.addParagraph().createRun(); int type = dataUrl.startsWith("data:image/png") ? XWPFDocument.PICTURE_TYPE_PNG : dataUrl.startsWith("data:image/webp") ? XWPFDocument.PICTURE_TYPE_JPEG : XWPFDocument.PICTURE_TYPE_JPEG; run.addPicture(new ByteArrayInputStream(bytes), type, "profile-photo", Units.toEMU(96), Units.toEMU(126)); } catch (Exception ignored) { } }
    private static String first(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value; return ""; }
    private static String join(String... values) { return java.util.Arrays.stream(values).filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(" · ")); }
}

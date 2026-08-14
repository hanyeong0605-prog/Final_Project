package com.jobpilot.api.domain.resume.controller;

import com.jobpilot.api.domain.resume.dto.ResumeDocumentResponse;
import com.jobpilot.api.domain.resume.dto.ResumeDraftRequest;
import com.jobpilot.api.domain.resume.entity.ResumeDocument;
import com.jobpilot.api.domain.resume.service.ResumeDocumentService;
import com.jobpilot.api.global.security.AuthenticatedMember;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/members/me/resume-documents")
public class ResumeDocumentController {
    private final ResumeDocumentService service;
    public ResumeDocumentController(ResumeDocumentService service) { this.service = service; }
    @GetMapping public List<ResumeDocumentResponse> list(Authentication auth) { return service.list(AuthenticatedMember.id(auth)); }
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
        try (XWPFDocument docx = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            String templateKey = document.getTemplateKey() == null ? "STANDARD" : document.getTemplateKey();
            for (String line : (document.getGeneratedContent() == null ? document.getExtractedText() : document.getGeneratedContent()).split("\\n")) {
                var paragraph = docx.createParagraph();
                var run = paragraph.createRun(); run.setText(line.replaceFirst("^#+\\s*", ""));
                if (line.startsWith("#")) { run.setBold(true); run.setFontSize("COMPACT".equals(templateKey) ? 12 : 15); }
                else if ("PROJECT".equals(templateKey)) run.setFontSize(11);
            }
            docx.write(output);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume-" + id + ".docx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).body(output.toByteArray());
        }
    }
}

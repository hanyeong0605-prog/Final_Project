package com.jobpilot.api.domain.resume.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobpilot.api.domain.member.entity.*;
import com.jobpilot.api.domain.member.repository.*;
import com.jobpilot.api.domain.matching.service.JobMatchRefreshScheduler;
import com.jobpilot.api.domain.resume.dto.ResumeDocumentResponse;
import com.jobpilot.api.domain.resume.dto.ResumeDraftRequest;
import com.jobpilot.api.domain.resume.entity.*;
import com.jobpilot.api.domain.resume.repository.ResumeDocumentRepository;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ResumeDocumentService {
    private final ResumeDocumentRepository documents; private final ResumeDocumentTextExtractor extractor;
    private final MemberRepository members; private final MemberProfileRepository profiles; private final MemberSpecificationRepository specs;
    private final MemberSkillRepository memberSkills; private final SkillRepository skillCatalog; private final CertificateRepository certificates; private final ProjectRepository projects;
    private final SelfIntroductionRepository introductions; private final ObjectMapper json; private final JobMatchRefreshScheduler refreshScheduler; private final ResumeDocumentAiClient aiClient;
    public ResumeDocumentService(ResumeDocumentRepository documents, ResumeDocumentTextExtractor extractor, MemberRepository members,
        MemberProfileRepository profiles, MemberSpecificationRepository specs, MemberSkillRepository memberSkills, SkillRepository skillCatalog, CertificateRepository certificates,
        ProjectRepository projects, SelfIntroductionRepository introductions, ObjectMapper json, JobMatchRefreshScheduler refreshScheduler, ResumeDocumentAiClient aiClient) {
        this.documents=documents; this.extractor=extractor; this.members=members; this.profiles=profiles; this.specs=specs; this.memberSkills=memberSkills;
        this.skillCatalog=skillCatalog; this.certificates=certificates; this.projects=projects; this.introductions=introductions; this.json=json; this.refreshScheduler=refreshScheduler; this.aiClient=aiClient;
    }
    public List<ResumeDocumentResponse> list(Long memberId) { return documents.findByMemberIdOrderByCreatedAtDesc(memberId).stream().map(ResumeDocumentResponse::from).toList(); }
    public ResumeDocumentResponse extract(Long memberId, MultipartFile file) {
        String text = extractor.extract(file);
        ObjectNode extracted = infer(text);
        enrichWithAi(extracted, text);
        String filename = file.getOriginalFilename();
        ResumeDocument document = documents.save(new ResumeDocument(memberId, ResumeDocumentType.UPLOADED,
                filename == null || filename.isBlank() ? "업로드 이력서" : filename, filename, text, null, extracted));
        return ResumeDocumentResponse.from(document);
    }
    public ResumeDocumentResponse applyProfile(Long memberId, Long documentId) {
        ResumeDocument document = owned(memberId, documentId); JsonNode data = document.getStructuredContent();
        if (data == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "추출된 이력서 정보가 없습니다.");
        Member member = members.findById(memberId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
        MemberProfile profile = profiles.findById(memberId).orElseGet(() -> new MemberProfile(memberId));
        MemberSpecification spec = specs.findById(memberId).orElseGet(() -> new MemberSpecification(memberId));
        String role = first(data, "targetRole", profile.getTargetRole());
        String major = first(data, "major", spec.getMajor());
        String education = first(data, "educationLevel", spec.getEducationLevel());
        String summary = merge(spec.getTechnicalSummary(), first(data, "technicalSummary", ""));
        int months = Math.max(spec.getTotalCareerMonths(), data.path("totalCareerMonths").asInt(0));
        ArrayNode locations = profile.getPreferredLocations() instanceof ArrayNode array ? array : json.createArrayNode();
        profile.update(empty(role), empty(profile.getTargetJobFamily()), locations, profile.getAvailableFrom(), empty(profile.getExperienceType()), empty(profile.getGithubUsername()));
        spec.update(empty(education), empty(spec.getSchoolName()), empty(major), empty(spec.getGraduationStatus()), months, empty(summary), empty(spec.getPortfolioUrl()));
        applyExtractedSkills(memberId, data.path("suggestedSkills"));
        applyExtractedCertificates(memberId, data.path("suggestedCertificates"));
        profiles.save(profile); specs.save(spec); member.completeOnboarding(); refreshScheduler.enqueueForMember(memberId);
        return ResumeDocumentResponse.from(document);
    }
    public ResumeDocumentResponse generate(Long memberId, ResumeDraftRequest request, MultipartFile templateFile) {
        MemberProfile profile=profiles.findById(memberId).orElse(null); MemberSpecification spec=specs.findById(memberId).orElse(null);
        String skillList=memberSkills.findByMemberId(memberId).stream()
                .map(v -> skillCatalog.findById(v.getSkillId()).map(Skill::getName).orElse(v.getNote()))
                .filter(v -> !blank(v)).collect(Collectors.joining(", "));
        String certList=certificates.findByMemberId(memberId).stream().map(Certificate::getName).collect(Collectors.joining(", "));
        String projectList=projects.findByMemberId(memberId).stream().map(Project::getTitle).collect(Collectors.joining(", "));
        String intro=introductions.findByMemberIdOrderByUpdatedAtDesc(memberId).stream().map(SelfIntroduction::getContent).findFirst().orElse("");
        String title=blank(request.title()) ? "Job-A-Dream 이력서 초안" : request.title().trim();
        String templateSource = templateFile == null || templateFile.isEmpty() ? "" : extractor.extract(templateFile);
        Set<String> enabledSections = request.enabledSections() == null || request.enabledSections().isEmpty()
                ? Set.of("profile", "skills", "certificates", "education", "projects") : Set.copyOf(request.enabledSections());
        String fallback = buildDraft(title, enabledSections.contains("profile") && profile != null ? profile.getTargetRole() : "",
                enabledSections.contains("skills") ? skillList : "",
                enabledSections.contains("education") && spec != null ? joinNonBlank(spec.getEducationLevel(), spec.getMajor()) : "",
                enabledSections.contains("profile") && spec != null ? spec.getTotalCareerMonths() : 0,
                enabledSections.contains("certificates") ? certList : "", enabledSections.contains("projects") ? projectList : "",
                enabledSections.contains("projects") ? intro : "",
                request.additionalRequest(), templateKey(request.templateKey()), templateSource);
        String content = generateWithAi(profile, spec, skillList, certList, projectList, intro, enabledSections, request, templateSource, fallback);
        ObjectNode metadata = json.createObjectNode();
        metadata.put("templateReference", !blank(templateSource));
        metadata.put("templateFilename", templateFile == null || templateFile.isEmpty() ? "" : empty(templateFile.getOriginalFilename()));
        ResumeDocument document=documents.save(new ResumeDocument(memberId, ResumeDocumentType.GENERATED, title,
                templateFile == null || templateFile.isEmpty() ? null : templateFile.getOriginalFilename(), null, content, metadata, templateKey(request.templateKey())));
        return ResumeDocumentResponse.from(document);
    }
    public ResumeDocument owned(Long memberId, Long id) { return documents.findByIdAndMemberId(id, memberId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이력서 문서를 찾을 수 없습니다.")); }
    private ObjectNode infer(String text) {
        String lower=text.toLowerCase(Locale.ROOT); ObjectNode node=json.createObjectNode(); ArrayNode skills=node.putArray("suggestedSkills");
        Set<String> foundSkills = new HashSet<>();
        for (Skill skill : skillCatalog.findAll()) {
            if (skill.isCanonical() && lower.contains(skill.getName().toLowerCase(Locale.ROOT))) foundSkills.add(skill.getName());
        }
        List.of(new String[] {"리액트", "React"}, new String[] {"스프링", "Spring"}, new String[] {"자바", "Java"}, new String[] {"파이썬", "Python"})
                .forEach(alias -> { if (lower.contains(alias[0].toLowerCase(Locale.ROOT))) foundSkills.add(alias[1]); });
        foundSkills.stream().sorted().limit(30).forEach(skills::add);
        ArrayNode certs=node.putArray("suggestedCertificates"); for (String value: List.of("정보처리기사","SQLD","ADsP","AWS")) if (text.contains(value)) certs.add(value);
        node.put("educationLevel", text.contains("대학교") || text.contains("학사") ? "BACHELOR" : "");
        node.put("major", keywordAfter(text, "학과", "전공")); node.put("targetRole", firstRole(text));
        node.put("totalCareerMonths", inferCareerMonths(text)); node.put("technicalSummary", summarize(text)); return node;
    }
    @SuppressWarnings("unchecked")
    private void enrichWithAi(ObjectNode extracted, String text) {
        try {
            Map<String, Object> result = aiClient.analyze(text);
            if (!Boolean.TRUE.equals(result.get("ok")) || !(result.get("profile") instanceof Map<?, ?> profile)) return;
            copyIfPresent(extracted, profile, "targetRole"); copyIfPresent(extracted, profile, "educationLevel");
            copyIfPresent(extracted, profile, "major"); copyIfPresent(extracted, profile, "technicalSummary");
            Object months = profile.get("totalCareerMonths"); if (months instanceof Number number && number.intValue() > extracted.path("totalCareerMonths").asInt()) extracted.put("totalCareerMonths", number.intValue());
            mergeArray(extracted.withArray("suggestedSkills"), profile.get("suggestedSkills"));
            mergeArray(extracted.withArray("suggestedCertificates"), profile.get("suggestedCertificates"));
        } catch (Exception ignored) { /* resume extraction keeps its deterministic fallback */ }
    }
    private void copyIfPresent(ObjectNode target, Map<?, ?> source, String key) {
        Object value=source.get(key); if (value instanceof String text && !blank(text)) target.put(key, text.trim());
    }
    private void mergeArray(ArrayNode target, Object values) {
        if (!(values instanceof List<?> list)) return;
        Set<String> existing=new HashSet<>(); target.forEach(value -> existing.add(normalize(value.asText())));
        for (Object value:list) { String text=String.valueOf(value).trim(); if (!blank(text) && existing.add(normalize(text))) target.add(text); }
    }
    @SuppressWarnings("unchecked")
    private String generateWithAi(MemberProfile profile, MemberSpecification spec, String skills, String certificates, String projects, String introduction,
            Set<String> enabledSections, ResumeDraftRequest request, String templateSource, String fallback) {
        try {
            Map<String,Object> context=new java.util.LinkedHashMap<>();
            context.put("targetRole", enabledSections.contains("profile") && profile != null ? empty(profile.getTargetRole()) : "");
            context.put("skills", enabledSections.contains("skills") ? skills : ""); context.put("certificates", enabledSections.contains("certificates") ? certificates : "");
            context.put("education", enabledSections.contains("education") && spec != null ? joinNonBlank(spec.getEducationLevel(), spec.getMajor()) : "");
            context.put("careerMonths", enabledSections.contains("profile") && spec != null ? spec.getTotalCareerMonths() : 0);
            context.put("projects", enabledSections.contains("projects") ? projects : ""); context.put("existingIntroduction", enabledSections.contains("projects") ? introduction : "");
            List<String> answers = request.answers() == null || request.answers().isEmpty() ? List.of(empty(request.additionalRequest())) : request.answers();
            Map<String,Object> result=aiClient.generate(context, answers, templateKey(request.templateKey()), templateSource);
            Object generated=result.get("content"); if (Boolean.TRUE.equals(result.get("ok")) && generated instanceof String value && !blank(value)) return "# " + (blank(request.title()) ? "Job-A-Dream 이력서 초안" : request.title().trim()) + "\n\n" + value.trim();
        } catch (Exception ignored) { /* downloadable local draft is still useful when Gemini is unavailable */ }
        return fallback;
    }
    private String firstRole(String text) { for(String v:List.of("백엔드 개발자","프론트엔드 개발자","풀스택 개발자","데이터 엔지니어","AI 엔지니어","개발자")) if(text.contains(v)) return v; return ""; }
    private int inferCareerMonths(String text) { var m=java.util.regex.Pattern.compile("(\\d+)\\s*년").matcher(text); return m.find() ? Integer.parseInt(m.group(1))*12 : 0; }
    private String keywordAfter(String text,String... keys){ for(String k:keys){int i=text.indexOf(k); if(i>0)return text.substring(Math.max(0,i-24),Math.min(text.length(),i+k.length())).replaceAll("[\\r\\n]+"," ").trim();} return ""; }
    private String summarize(String text){ return text.replaceAll("\\s+"," ").trim().substring(0, Math.min(800, text.replaceAll("\\s+"," ").trim().length())); }
    private String first(JsonNode node,String field,String fallback){String v=node.path(field).asText(""); return blank(v)?fallback:v;}
    private String templateKey(String value) {
        if ("PROJECT".equalsIgnoreCase(value)) return "PROJECT";
        if ("COMPACT".equalsIgnoreCase(value)) return "COMPACT";
        return "STANDARD";
    }
    private void applyExtractedSkills(Long memberId, JsonNode candidates) {
        if (!candidates.isArray()) return;
        Set<Long> owned = memberSkills.findByMemberId(memberId).stream().map(MemberSkill::getSkillId).collect(Collectors.toSet());
        List<MemberSkill> additions = new java.util.ArrayList<>();
        for (JsonNode candidate : candidates) {
            String name = candidate.asText("").trim();
            skillCatalog.findByName(name).filter(Skill::isCanonical).filter(skill -> !owned.contains(skill.getId()))
                    .ifPresent(skill -> { additions.add(new MemberSkill(memberId, skill.getId(), "LEARNING", "이력서에서 추출")); owned.add(skill.getId()); });
        }
        if (!additions.isEmpty()) memberSkills.saveAll(additions);
    }
    private void applyExtractedCertificates(Long memberId, JsonNode candidates) {
        if (!candidates.isArray()) return;
        Set<String> owned = certificates.findByMemberId(memberId).stream().map(Certificate::getName).map(this::normalize).collect(Collectors.toSet());
        List<Certificate> additions = new java.util.ArrayList<>();
        for (JsonNode candidate : candidates) {
            String name = candidate.asText("").trim();
            if (!name.isBlank() && owned.add(normalize(name))) additions.add(new Certificate(memberId, name, "이력서 추출", null, null, null));
        }
        if (!additions.isEmpty()) certificates.saveAll(additions);
    }
    private String buildDraft(String title, String role, String skills, String education, int careerMonths, String certificates,
            String projects, String introduction, String additionalRequest, String template, String templateSource) {
        String target = "## 지원 직무\n" + value(role, "지원 직무를 입력해 주세요");
        String skill = "## 핵심 역량\n" + value(skills, "보유 기술을 추가해 주세요");
        String experience = "## 학력 및 경력\n" + value(education, "학력 정보를 입력해 주세요") + "\n경력 " + careerMonths + "개월";
        String certificate = "## 자격증\n" + value(certificates, "보유 자격증을 추가해 주세요");
        String project = "## 프로젝트\n" + value(projects, "프로젝트 경험을 추가해 주세요");
        String intro = "## 자기소개\n" + value(introduction, "지원 직무와 연결되는 경험을 STAR 방식으로 작성해 주세요");
        List<String> sections = "PROJECT".equals(template)
                ? List.of(target, project, skill, certificate, experience, intro)
                : "COMPACT".equals(template) ? List.of(target, skill, experience, project, intro)
                : List.of(target, skill, experience, certificate, project, intro);
        String attachmentNote = blank(templateSource) ? "" : "\n\n## 첨부 양식 참고\n첨부한 양식의 항목 순서를 참고해 작성한 편집 가능한 초안입니다.";
        String requestNote = blank(additionalRequest) ? "" : "\n\n## 작성 요청 반영\n" + additionalRequest.trim();
        return "# " + title + "\n\n" + String.join("\n\n", sections) + requestNote + attachmentNote;
    }
    private String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
    private String merge(String current,String extracted){ return blank(current)?extracted:(blank(extracted)?current:current+"\n"+extracted); }
    private String empty(String v){return v==null?"":v;} private boolean blank(String v){return v==null||v.isBlank();} private String value(String v,String fallback){return blank(v)?fallback:v;} private String joinNonBlank(String a,String b){return (empty(a)+" "+empty(b)).trim();}
}

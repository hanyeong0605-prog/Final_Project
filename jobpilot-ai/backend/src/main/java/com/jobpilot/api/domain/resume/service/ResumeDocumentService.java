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
import com.jobpilot.api.domain.resume.repository.ResumeEntryRepository;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final SelfIntroductionRepository introductions; private final ResumeEntryRepository resumeEntries; private final ObjectMapper json; private final JobMatchRefreshScheduler refreshScheduler; private final ResumeDocumentAiClient aiClient; private final ResumeAiConsentService aiConsent;
    public ResumeDocumentService(ResumeDocumentRepository documents, ResumeDocumentTextExtractor extractor, MemberRepository members,
        MemberProfileRepository profiles, MemberSpecificationRepository specs, MemberSkillRepository memberSkills, SkillRepository skillCatalog, CertificateRepository certificates,
        ProjectRepository projects, SelfIntroductionRepository introductions, ResumeEntryRepository resumeEntries, ObjectMapper json, JobMatchRefreshScheduler refreshScheduler, ResumeDocumentAiClient aiClient, ResumeAiConsentService aiConsent) {
        this.documents=documents; this.extractor=extractor; this.members=members; this.profiles=profiles; this.specs=specs; this.memberSkills=memberSkills;
        this.skillCatalog=skillCatalog; this.certificates=certificates; this.projects=projects; this.introductions=introductions; this.resumeEntries=resumeEntries; this.json=json; this.refreshScheduler=refreshScheduler; this.aiClient=aiClient; this.aiConsent=aiConsent;
    }
    public List<ResumeDocumentResponse> list(Long memberId) { return documents.findByMemberIdOrderByCreatedAtDesc(memberId).stream().map(ResumeDocumentResponse::from).toList(); }
    public void delete(Long memberId, Long documentId) {
        if (documents.deleteByIdAndMemberId(documentId, memberId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이력서 자료를 찾을 수 없습니다.");
        }
    }
    public ResumeDocumentResponse extract(Long memberId, MultipartFile file) {
        String text = extractor.extract(file);
        ObjectNode extracted = inferStructured(text);
        // Uploading a resume is useful even without AI consent: local extraction still finds
        // obvious values. Only the explicit AI analysis sends text to the external model.
        if (aiConsent.hasAgreed(memberId)) enrichWithAi(extracted, text);
        else extracted.put("analysisWarning", "AI 분석은 이력서 정보 처리 동의 후 사용할 수 있습니다. 현재는 파일 안에서 직접 확인한 정보만 제안합니다.");
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
        String schoolName = first(data, "schoolName", spec.getSchoolName());
        String major = first(data, "major", spec.getMajor());
        String education = first(data, "educationLevel", spec.getEducationLevel());
        String graduationStatus = first(data, "graduationStatus", spec.getGraduationStatus());
        String summary = merge(sanitizeSummary(spec.getTechnicalSummary()),
                sanitizeSummary(first(data, "technicalSummary", "")));
        int months = Math.max(spec.getTotalCareerMonths(), data.path("totalCareerMonths").asInt(0));
        ArrayNode locations = profile.getPreferredLocations() instanceof ArrayNode array ? array : json.createArrayNode();
        profile.update(empty(role), empty(profile.getTargetJobFamily()), locations, profile.getAvailableFrom(), empty(profile.getExperienceType()), empty(profile.getGithubUsername()));
        spec.update(empty(education), empty(schoolName), empty(major), empty(graduationStatus), months, empty(summary), empty(spec.getPortfolioUrl()));
        applyExtractedSkills(memberId, data.path("suggestedSkills"));
        applyExtractedCertificates(memberId, data.path("suggestedCertificates"));
        applyPersonalEntry(memberId, data.path("personalInfo"));
        applyEducationEntry(memberId, data);
        profiles.save(profile); specs.save(spec); member.completeOnboarding(); refreshScheduler.enqueueForMember(memberId);
        return ResumeDocumentResponse.from(document);
    }
    public ResumeDocumentResponse generate(Long memberId, ResumeDraftRequest request, MultipartFile templateFile) {
        aiConsent.requireAgreed(memberId);
        MemberProfile profile=profiles.findById(memberId).orElse(null); MemberSpecification spec=specs.findById(memberId).orElse(null);
        String skillList=memberSkills.findByMemberId(memberId).stream()
                .map(v -> skillCatalog.findById(v.getSkillId()).map(Skill::getName).orElse(v.getNote()))
                .filter(v -> !blank(v)).collect(Collectors.joining(", "));
        String certList=certificates.findByMemberId(memberId).stream().map(Certificate::getName).collect(Collectors.joining(", "));
        String projectList=projects.findByMemberId(memberId).stream().map(Project::getTitle).collect(Collectors.joining(", "));
        List<Map<String, Object>> detailedEntries = resumeEntries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId).stream()
                .map(entry -> Map.<String, Object>of("type", entry.getEntryType().name(), "title", entry.getTitle(), "content", json.convertValue(entry.getContent(), Map.class))).toList();
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
        String content = generateWithAi(profile, spec, skillList, certList, projectList, intro, detailedEntries, enabledSections, request, templateSource, fallback);
        ObjectNode metadata = json.createObjectNode();
        metadata.put("templateReference", !blank(templateSource));
        metadata.put("templateFilename", templateFile == null || templateFile.isEmpty() ? "" : empty(templateFile.getOriginalFilename()));
        ResumeDocument document=documents.save(new ResumeDocument(memberId, ResumeDocumentType.GENERATED, title,
                templateFile == null || templateFile.isEmpty() ? null : templateFile.getOriginalFilename(), null, content, metadata, templateKey(request.templateKey())));
        return ResumeDocumentResponse.from(document);
    }
    public ResumeDocument owned(Long memberId, Long id) { return documents.findByIdAndMemberId(id, memberId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이력서 문서를 찾을 수 없습니다.")); }
    /**
     * Extract the fields that can be reflected in a member profile without relying
     * on an LLM. This is deliberately a conservative fallback: it only emits a
     * value when the resume actually contains one, instead of copying section
     * headings such as "학력사항" into the profile summary.
     */
    private ObjectNode inferStructured(String source) {
        String text = source == null ? "" : source.replace('\u00a0', ' ');
        String lower = text.toLowerCase(Locale.ROOT);
        ObjectNode node = json.createObjectNode();
        ArrayNode skills = node.putArray("suggestedSkills");
        Set<String> foundSkills = new java.util.TreeSet<>();
        for (Skill skill : skillCatalog.findAll()) {
            if (skill.isCanonical() && containsTerm(lower, skill.getName())) foundSkills.add(skill.getName());
        }
        Map<String, String> aliases = new java.util.LinkedHashMap<>();
        aliases.put("react", "React"); aliases.put("리액트", "React");
        aliases.put("spring", "Spring"); aliases.put("스프링", "Spring");
        aliases.put("java", "Java"); aliases.put("자바", "Java");
        aliases.put("python", "Python"); aliases.put("파이썬", "Python");
        aliases.put("javascript", "JavaScript"); aliases.put("typescript", "TypeScript");
        aliases.put("aws", "AWS"); aliases.put("docker", "Docker"); aliases.put("kubernetes", "Kubernetes");
        aliases.forEach((alias, canonical) -> { if (lower.contains(alias)) foundSkills.add(canonical); });
        foundSkills.stream().limit(30).forEach(skills::add);

        ArrayNode certificates = node.putArray("suggestedCertificates");
        for (String certificate : List.of("정보처리기사", "정보처리산업기사", "정보처리기능사", "SQLD", "SQLP", "ADsP", "ADP", "AWS Certified", "컴퓨터활용능력", "ITQ", "운전면허", "1종보통", "2종보통", "OPIc", "토익")) {
            if (text.contains(certificate)) certificates.add(certificate);
        }
        node.put("educationLevel", inferEducationLevel(text));
        node.put("schoolName", inferSchoolName(text));
        node.put("major", inferMajor(text));
        node.put("graduationStatus", inferGraduationStatus(text));
        node.put("targetRole", inferTargetRole(text));
        node.put("totalCareerMonths", inferCareerMonthsStructured(text));
        ObjectNode personal = node.putObject("personalInfo");
        putIfFound(personal, "name", find(text, "(?:성명|이름)\\s*(?:[|:：]\s*한글)?\\s*[|:：]?\\s*([가-힣]{2,5})"));
        putIfFound(personal, "hanjaName", find(text, "한자\\s*[|:：]?\\s*([一-龥]{2,8})"));
        putIfFound(personal, "birthDate", findBirthDate(text));
        putIfFound(personal, "email", find(text, "([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})"));
        putIfFound(personal, "phone", find(text, "(01[016789][- ]?\\d{3,4}[- ]?\\d{4})"));
        // 문서 제목이 반복된 원문을 "추가 설명"으로 저장하지 않는다. 기술은 별도 기술 스택으로만 반영한다.
        node.put("technicalSummary", "");
        return node;
    }

    private boolean containsTerm(String text, String term) {
        return !blank(term) && text.contains(term.toLowerCase(Locale.ROOT));
    }

    private String inferEducationLevel(String text) {
        if (text.contains("박사")) return "DOCTOR";
        if (text.contains("석사")) return "MASTER";
        if (text.contains("대학교") || text.contains("대학") || text.contains("학사")) return "BACHELOR";
        if (text.contains("전문학사") || text.contains("전문대")) return "ASSOCIATE";
        if (text.contains("고등학교") || text.contains("고졸")) return "HIGH_SCHOOL";
        return "";
    }

    private String inferSchoolName(String text) {
        Matcher matcher = Pattern.compile("([가-힣A-Za-z0-9·() ]{2,40}(?:대학교|대학|고등학교))").matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String inferMajor(String text) {
        Matcher labelled = Pattern.compile("(?m)(?:전공|학과)\\s*[:：|]?\\s*([가-힣A-Za-z0-9·() /-]{2,50})").matcher(text);
        if (labelled.find()) return cleanField(labelled.group(1));
        Matcher named = Pattern.compile("([가-힣A-Za-z0-9·() ]{2,40}(?:학과|학부|전공))").matcher(text);
        return named.find() ? named.group(1).trim() : "";
    }

    private String cleanField(String value) {
        return value.replaceAll("[|\\r\\n]+", " ").trim();
    }

    private String inferGraduationStatus(String text) {
        if (text.contains("졸업예정")) return "EXPECTED";
        if (text.contains("졸업")) return "GRADUATED";
        if (text.contains("재학")) return "ENROLLED";
        return "";
    }

    private String inferTargetRole(String text) {
        for (String role : List.of("백엔드 개발자", "프론트엔드 개발자", "풀스택 개발자", "웹 개발자", "모바일 개발자", "데이터 분석가", "데이터 엔지니어", "AI 엔지니어", "머신러닝 엔지니어", "UI/UX 디자이너", "기획자")) {
            if (text.contains(role)) return role;
        }
        return "";
    }

    private int inferCareerMonthsStructured(String text) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*년\\s*(?:(\\d+)\\s*개월?)?").matcher(text);
        int maximum = 0;
        while (matcher.find()) {
            int years = Integer.parseInt(matcher.group(1));
            int months = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            // 생년월일의 "2000년 06월" 같은 날짜를 경력으로 오인하지 않는다.
            if (years > 0 && years <= 60 && months < 12) maximum = Math.max(maximum, years * 12 + months);
        }
        return maximum;
    }

    private String find(String text, String pattern) {
        Matcher matcher = Pattern.compile(pattern, Pattern.MULTILINE).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
    private String findBirthDate(String text) {
        Matcher matcher = Pattern.compile("(\\d{4})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일").matcher(text);
        return matcher.find() ? String.format("%s-%02d-%02d", matcher.group(1), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))) : "";
    }
    private void putIfFound(ObjectNode target, String key, String value) { if (!blank(value)) target.put(key, value); }

    private String summarizeExtracted(String text, Set<String> skills) {
        List<String> lines = java.util.Arrays.stream(text.split("\\R"))
                .map(String::trim).filter(line -> line.length() >= 12).filter(line -> !isHeadingLine(line))
                .distinct().limit(3).toList();
        String summary = String.join(" ", lines);
        if (summary.length() > 500) summary = summary.substring(0, 500);
        if (!skills.isEmpty()) {
            String prefix = "확인된 기술: " + String.join(", ", skills) + ".";
            return blank(summary) ? prefix : prefix + " " + summary;
        }
        return summary;
    }

    private boolean isHeadingLine(String value) {
        String compact = value.replaceAll("[\\s\\d.·|:-]", "");
        return compact.matches("^(이력서|학력사항|교육사항|수행프로젝트|직무능력사항|직장경력사항|자격및면허취득사항|병역사항|자기소개서)+$");
    }

    private String sanitizeSummary(String value) {
        if (blank(value)) return "";
        if (value.contains("이력서") && value.contains("학력사항") && value.contains("자기소개서") && value.length() < 500) return "";
        return value;
    }

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
            if (!Boolean.TRUE.equals(result.get("ok")) || !(result.get("profile") instanceof Map<?, ?> profile)) {
                Object message = result.get("message");
                if (message != null && !blank(String.valueOf(message))) extracted.put("analysisWarning", String.valueOf(message));
                return;
            }
            copyIfPresent(extracted, profile, "targetRole"); copyIfPresent(extracted, profile, "educationLevel");
            copyIfPresent(extracted, profile, "schoolName"); copyIfPresent(extracted, profile, "major");
            copyIfPresent(extracted, profile, "graduationStatus"); copyIfPresent(extracted, profile, "technicalSummary");
            Object months = profile.get("totalCareerMonths"); if (months instanceof Number number && number.intValue() > extracted.path("totalCareerMonths").asInt()) extracted.put("totalCareerMonths", number.intValue());
            mergeArray(extracted.withArray("suggestedSkills"), profile.get("suggestedSkills"));
            mergeArray(extracted.withArray("suggestedCertificates"), profile.get("suggestedCertificates"));
        } catch (Exception exception) {
            extracted.put("analysisWarning", "AI 분석을 사용할 수 없어 이력서의 텍스트를 기준으로 추출했습니다.");
        }
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
    private String generateWithAi(MemberProfile profile, MemberSpecification spec, String skills, String certificates, String projects, String introduction, List<Map<String, Object>> detailedEntries,
            Set<String> enabledSections, ResumeDraftRequest request, String templateSource, String fallback) {
        try {
            Map<String,Object> context=new java.util.LinkedHashMap<>();
            context.put("targetRole", enabledSections.contains("profile") && profile != null ? empty(profile.getTargetRole()) : "");
            context.put("skills", enabledSections.contains("skills") ? skills : ""); context.put("certificates", enabledSections.contains("certificates") ? certificates : "");
            context.put("education", enabledSections.contains("education") && spec != null ? joinNonBlank(spec.getEducationLevel(), spec.getMajor()) : "");
            context.put("careerMonths", enabledSections.contains("profile") && spec != null ? spec.getTotalCareerMonths() : 0);
            context.put("projects", enabledSections.contains("projects") ? projects : ""); context.put("existingIntroduction", enabledSections.contains("projects") ? introduction : "");
            context.put("detailedEntries", detailedEntries);
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
    private void applyPersonalEntry(Long memberId, JsonNode personal) {
        if (personal == null || !personal.isObject() || personal.isEmpty()) return;
        String name = personal.path("name").asText("").trim();
        if (name.isBlank()) return;
        ResumeEntry existing = resumeEntries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId).stream()
                .filter(entry -> entry.getEntryType() == ResumeEntryType.PERSONAL).findFirst().orElse(null);
        ObjectNode content = existing != null && existing.getContent().isObject()
                ? (ObjectNode) existing.getContent().deepCopy() : json.createObjectNode();
        for (String key : List.of("name", "hanjaName", "birthDate", "email", "phone", "address")) {
            String value = personal.path(key).asText("").trim();
            if (!value.isBlank()) content.put(key, value);
        }
        if (existing == null) resumeEntries.save(new ResumeEntry(memberId, ResumeEntryType.PERSONAL, "인적사항", content, 0));
        else existing.update("인적사항", content, existing.getDisplayOrder());
    }
    private void applyEducationEntry(Long memberId, JsonNode data) {
        String school = data.path("schoolName").asText("").trim();
        String major = data.path("major").asText("").trim();
        if (school.isBlank() && major.isBlank()) return;
        ResumeEntry existing = resumeEntries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId).stream()
                .filter(entry -> entry.getEntryType() == ResumeEntryType.EDUCATION).findFirst().orElse(null);
        ObjectNode content = existing != null && existing.getContent().isObject()
                ? (ObjectNode) existing.getContent().deepCopy() : json.createObjectNode();
        for (String key : List.of("schoolName", "major", "educationLevel", "graduationStatus")) {
            String value = data.path(key).asText("").trim(); if (!value.isBlank()) content.put(switch (key) { case "schoolName" -> "school"; case "educationLevel" -> "degree"; case "graduationStatus" -> "status"; default -> key; }, value);
        }
        String title = school.isBlank() ? "학력" : school;
        if (existing == null) resumeEntries.save(new ResumeEntry(memberId, ResumeEntryType.EDUCATION, title, content, 0));
        else existing.update(title, content, existing.getDisplayOrder());
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

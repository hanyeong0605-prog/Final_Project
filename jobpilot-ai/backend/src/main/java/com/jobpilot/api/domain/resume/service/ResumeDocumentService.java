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
import java.time.LocalDate;
import java.util.Base64;
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
    private static final int MAX_PROFILE_SKILLS = 40;
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
    /** Small, factual preview for the writing wizard. No AI call is made here. */
    public Map<String, List<String>> draftContext(Long memberId) {
        MemberProfile profile = profiles.findById(memberId).orElse(null);
        MemberSpecification spec = specs.findById(memberId).orElse(null);
        List<String> profileFacts = new java.util.ArrayList<>();
        if (profile != null && !blank(profile.getTargetRole())) profileFacts.add("희망 직무 · " + profile.getTargetRole());
        if (spec != null && spec.getTotalCareerMonths() > 0) profileFacts.add("총 실무경력 · " + careerText(spec.getTotalCareerMonths()));
        if (spec != null && !blank(spec.getTechnicalSummary())) profileFacts.add("기술 요약 · " + shorten(spec.getTechnicalSummary(), 140));
        List<String> skills = memberSkills.findByMemberId(memberId).stream()
                .map(item -> skillCatalog.findById(item.getSkillId()).map(Skill::getName).orElse(item.getNote()))
                .filter(value -> !blank(value)).distinct().map(value -> "보유 기술 · " + value).toList();
        List<String> certificateFacts = certificates.findByMemberId(memberId).stream().map(Certificate::getName)
                .filter(value -> !blank(value)).distinct().map(value -> "자격증 · " + value).toList();
        List<String> educationFacts = new java.util.ArrayList<>();
        if (spec != null && !blank(joinNonBlank(spec.getSchoolName(), spec.getMajor()))) educationFacts.add("학력 · " + joinNonBlank(spec.getSchoolName(), spec.getMajor()));
        resumeEntries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId).stream()
                .filter(entry -> entry.getEntryType() == ResumeEntryType.EDUCATION || entry.getEntryType() == ResumeEntryType.TRAINING || entry.getEntryType() == ResumeEntryType.CAREER)
                .map(entry -> entry.getEntryType().name() + " · " + entry.getTitle() + entrySummary(entry.getContent()))
                .filter(value -> !blank(value)).forEach(educationFacts::add);
        List<String> projectFacts = projects.findByMemberId(memberId).stream()
                .map(project -> "프로젝트 · " + project.getTitle() + nonBlankSuffix(project.getRoleDescription()))
                .filter(value -> !blank(value)).toList();
        return Map.of("profile", profileFacts, "skills", skills, "certificates", certificateFacts,
                "education", educationFacts, "projects", projectFacts);
    }
    public void delete(Long memberId, Long documentId) {
        if (documents.deleteByIdAndMemberId(documentId, memberId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이력서 자료를 찾을 수 없습니다.");
        }
    }
    public ResumeDocumentResponse extract(Long memberId, MultipartFile file) {
        String text = extractor.extract(file);
        ObjectNode extracted = inferStructured(text, extractor.extractTableRows(file));
        ResumeDocumentTextExtractor.PhotoCandidate photo = extractor.extractPhoto(file);
        if (photo != null) extracted.put("profilePhotoDataUrl", "data:" + photo.contentType() + ";base64," + Base64.getEncoder().encodeToString(photo.bytes()));
        // Uploading a resume is useful even without AI consent: local extraction still finds
        // obvious values. Only the explicit AI analysis sends text to the external model.
        if (aiConsent.hasAgreed(memberId)) enrichWithAi(extracted, text);
        else extracted.put("analysisWarning", "AI 분석은 이력서 정보 처리 동의 후 사용할 수 있습니다. 현재는 파일 안에서 직접 확인한 정보만 제안합니다.");
        MemberProfile savedProfile = profiles.findById(memberId).orElse(null);
        String targetContext = String.join(" ", extracted.path("targetRole").asText(""),
                savedProfile == null ? "" : empty(savedProfile.getTargetJobFamily()),
                savedProfile == null ? "" : empty(savedProfile.getTargetRole()));
        classifyCareerRelevance(extracted, targetContext);
        normalizeCertificateSuggestions(extracted);
        normalizeStructuredProfile(extracted);
        annotateSkillImport(memberId, extracted, text);
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
        String role = preserve(profile.getTargetRole(), data.path("targetRole").asText());
        String schoolName = preserve(spec.getSchoolName(), data.path("schoolName").asText());
        String major = preserve(spec.getMajor(), data.path("major").asText());
        String education = preserve(spec.getEducationLevel(), data.path("educationLevel").asText());
        String graduationStatus = preserve(spec.getGraduationStatus(), data.path("graduationStatus").asText());
        String summary = merge(sanitizeSummary(spec.getTechnicalSummary()),
                sanitizeSummary(first(data, "technicalSummary", "")));
        int months = "JOB_RELEVANT".equals(data.path("careerMonthsPolicy").asText())
                ? data.path("totalCareerMonths").asInt(0)
                : Math.max(spec.getTotalCareerMonths(), data.path("totalCareerMonths").asInt(0));
        ArrayNode locations = profile.getPreferredLocations() instanceof ArrayNode array ? array : json.createArrayNode();
        // 이력서 반영 경로가 직무 분야·희망 지역을 빈 값으로 저장해 공개 인재 화면에서
        // 점(.)만 보이던 문제를 막는다. 추출된 희망 직무는 분야의 기본값으로도 쓰고,
        // 개인 정보의 주소가 있으면 첫 희망 지역으로 보관한다.
        String family = preserve(profile.getTargetJobFamily(), data.path("targetJobFamily").asText());
        if (blank(family)) family = role;
        if (locations.isEmpty()) {
            String address = data.path("personalInfo").path("address").asText("").trim();
            if (!address.isBlank()) locations.add(address);
        }
        String experienceType = blank(profile.getExperienceType()) ? (months > 0 ? "EXPERIENCED" : "ENTRY") : profile.getExperienceType();
        profile.update(empty(role), empty(family), locations, profile.getAvailableFrom(), empty(experienceType), empty(profile.getGithubUsername()));
        spec.update(empty(education), empty(schoolName), empty(major), empty(graduationStatus), months, empty(summary), empty(spec.getPortfolioUrl()));
        String photoDataUrl = data.path("profilePhotoDataUrl").asText("");
        applyExtractedPhoto(spec, photoDataUrl);
        // 이전에 분석되어 저장된 이력서는 autoSelectedSkills 필드가 없으므로,
        // 그 경우에는 기존 suggestedSkills를 사용한다. 두 경우 모두 저장 한도는 적용된다.
        JsonNode skillsToApply = data.has("autoSelectedSkills")
                ? data.path("autoSelectedSkills")
                : data.path("suggestedSkills");
        applyExtractedSkills(memberId, skillsToApply);
        applyExtractedCertificates(memberId, data.path("certificateDetails"), data.path("suggestedCertificates"));
        applyPersonalEntry(memberId, data.path("personalInfo"));
        applyStructuredEntries(memberId, data);
        profiles.save(profile); specs.save(spec); member.completeOnboarding(); refreshScheduler.enqueueForMember(memberId);
        return ResumeDocumentResponse.from(document);
    }
    /**
     * The importer may understand any layout, but only this small canonical profile is
     * editable before it reaches the member profile.  Keeping review data on the uploaded
     * document makes every built-in Word template consume the same confirmed facts.
     */
    public ResumeDocumentResponse reviewExtraction(Long memberId, Long documentId, JsonNode review) {
        ResumeDocument document = owned(memberId, documentId);
        if (document.getDocumentType() != ResumeDocumentType.UPLOADED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드한 이력서만 검토할 수 있습니다.");
        }
        ObjectNode extracted = document.getStructuredContent() instanceof ObjectNode value
                ? value.deepCopy() : json.createObjectNode();
        if (review != null && review.isObject()) {
            ObjectNode personal = extracted.path("personalInfo") instanceof ObjectNode value
                    ? value : extracted.putObject("personalInfo");
            JsonNode incomingPersonal = review.path("personalInfo");
            for (String key : List.of("name", "hanjaName", "birthDate", "email", "phone", "address")) {
                String value = incomingPersonal.path(key).asText("").trim();
                if (!value.isBlank()) personal.put(key, value);
            }
            for (String key : List.of("targetRole", "schoolName", "major", "educationLevel", "graduationStatus")) {
                String value = review.path(key).asText("").trim();
                if (!value.isBlank()) extracted.put(key, value);
            }
            String skillText = review.path("skills").asText("");
            if (!skillText.isBlank()) {
                ArrayNode skills = extracted.putArray("suggestedSkills");
                Set<String> unique = new java.util.LinkedHashSet<>();
                for (String skill : skillText.split("[,\\n]")) if (!blank(skill)) unique.add(skill.trim());
                unique.stream().limit(MAX_PROFILE_SKILLS).forEach(skills::add);
            }
            JsonNode project = review.path("project");
            if (project.isObject() && !blank(project.path("title").asText())) {
                ArrayNode portfolios = extracted.withArray("portfolios");
                ObjectNode first = portfolios.isEmpty() ? portfolios.addObject() : (ObjectNode) portfolios.get(0);
                for (String key : List.of("title", "startedAt", "endedAt", "description")) {
                    String value = project.path(key).asText("").trim(); if (!value.isBlank()) first.put(key, value);
                }
                String role = project.path("role").asText("").trim(); String skills = project.path("skills").asText("").trim();
                if (!role.isBlank() || !skills.isBlank()) first.put("description", java.util.stream.Stream.of(
                        !role.isBlank() ? "역할: " + role : "", !skills.isBlank() ? "기술: " + skills : "", first.path("description").asText(""))
                        .filter(value -> !blank(value)).collect(Collectors.joining("\n")));
            }
        }
        normalizeStructuredProfile(extracted);
        annotateSkillImport(memberId, extracted, document.getExtractedText());
        document.updateStructuredContent(extracted);
        return ResumeDocumentResponse.from(document);
    }
    public ResumeDocumentResponse generate(Long memberId, ResumeDraftRequest request, MultipartFile templateFile) {
        aiConsent.requireAgreed(memberId);
        Member member=members.findById(memberId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
        MemberProfile profile=profiles.findById(memberId).orElse(null); MemberSpecification spec=specs.findById(memberId).orElse(null);
        String skillList=memberSkills.findByMemberId(memberId).stream()
                .map(v -> skillCatalog.findById(v.getSkillId()).map(Skill::getName).orElse(v.getNote()))
                .filter(v -> !blank(v)).collect(Collectors.joining(", "));
        String certList=certificates.findByMemberId(memberId).stream().map(Certificate::getName).collect(Collectors.joining(", "));
        List<Map<String, Object>> detailedEntries = resumeEntries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId).stream()
                .map(entry -> Map.<String, Object>of("type", entry.getEntryType().name(), "title", entry.getTitle(), "content", json.convertValue(entry.getContent(), Map.class))).toList();
        JsonNode detailedEntryData = json.valueToTree(detailedEntries);
        List<Map<String, String>> projectItems = projectTemplateItems(memberId, detailedEntryData);
        String projectList=projectItems.stream().map(item -> item.getOrDefault("title", "")).filter(value -> !blank(value)).collect(Collectors.joining(", "));
        List<SelfIntroduction> selfIntroductionList = introductions.findByMemberIdOrderByUpdatedAtDesc(memberId);
        String intro=selfIntroductionList.stream().map(SelfIntroduction::getContent).findFirst().orElse("");
        String selectedTemplate = templateKey(request.templateKey());
        String title=blank(request.title()) ? templateTitle(selectedTemplate) : request.title().trim();
        String templateSource = templateFile == null || templateFile.isEmpty() ? "" : extractor.extract(templateFile);
        // A blank example inside an uploaded form is layout guidance, never resume evidence.
        // Built-in forms use a fixed field map, so no sample wording is sent to the model.
        String aiTemplateHint = blank(templateSource) ? builtInTemplateHint(selectedTemplate) : "업로드 양식의 항목 순서만 참고한다. 양식 안의 예시 인명·회사·문장·수치는 절대 사용하지 않는다.";
        Set<String> enabledSections = request.enabledSections() == null || request.enabledSections().isEmpty()
                ? Set.of("profile", "skills", "certificates", "education", "projects") : Set.copyOf(request.enabledSections());
        String fallback = buildDraft(title, enabledSections.contains("profile") && profile != null ? profile.getTargetRole() : "",
                enabledSections.contains("skills") ? skillList : "",
                enabledSections.contains("education") && spec != null ? joinNonBlank(spec.getEducationLevel(), spec.getMajor()) : "",
                enabledSections.contains("profile") && spec != null ? spec.getTotalCareerMonths() : 0,
                enabledSections.contains("certificates") ? certList : "", enabledSections.contains("projects") ? projectList : "",
                enabledSections.contains("projects") ? intro : "",
                request.additionalRequest(), selectedTemplate, templateSource);
        String content = generateWithAi(profile, spec, skillList, certList, projectList, intro, detailedEntries, enabledSections, request, aiTemplateHint, fallback);
        ObjectNode metadata = json.createObjectNode();
        metadata.put("templateReference", !blank(templateSource));
        metadata.put("templateFilename", templateFile == null || templateFile.isEmpty() ? "" : empty(templateFile.getOriginalFilename()));
        ObjectNode templateData = metadata.putObject("templateData");
        JsonNode personal = resumeEntries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId).stream()
                .filter(entry -> entry.getEntryType() == ResumeEntryType.PERSONAL).map(ResumeEntry::getContent).findFirst().orElse(json.createObjectNode());
        templateData.put("name", personal.path("name").asText(member.getNickname()));
        templateData.put("hanjaName", personal.path("hanjaName").asText(""));
        templateData.put("birthDate", personal.path("birthDate").asText(""));
        templateData.put("email", personal.path("email").asText(member.getEmail()));
        templateData.put("phone", personal.path("phone").asText("")); templateData.put("address", personal.path("address").asText(""));
        templateData.put("targetRole", profile == null ? "" : empty(profile.getTargetRole()));
        templateData.put("careerMonths", spec == null ? 0 : spec.getTotalCareerMonths());
        templateData.put("schoolName", spec == null ? "" : empty(spec.getSchoolName())); templateData.put("major", spec == null ? "" : empty(spec.getMajor()));
        templateData.put("educationLevel", spec == null ? "" : empty(spec.getEducationLevel())); templateData.put("graduationStatus", spec == null ? "" : empty(spec.getGraduationStatus()));
        templateData.put("technicalSummary", spec == null ? "" : empty(spec.getTechnicalSummary()));
        templateData.put("profilePhotoDataUrl", spec == null ? "" : photoDataUrl(spec));
        templateData.put("skills", enabledSections.contains("skills") ? skillList : ""); templateData.put("certificates", enabledSections.contains("certificates") ? certList : "");
        templateData.set("entries", detailedEntryData);
        templateData.set("answers", json.valueToTree(request.answers() == null ? List.of() : request.answers()));
        templateData.set("selfIntroductions", json.valueToTree(selfIntroductionList.stream().map(item -> Map.of(
                "title", empty(item.getTitle()), "content", empty(item.getContent()))).toList()));
        templateData.set("certificateDetails", json.valueToTree(certificates.findByMemberId(memberId).stream().map(certificate -> Map.of(
                "name", empty(certificate.getName()), "issuer", empty(certificate.getIssuer()),
                "acquiredAt", certificate.getAcquiredAt() == null ? "" : certificate.getAcquiredAt().toString())).toList()));
        templateData.set("projects", json.valueToTree(projectItems));
        templateData.put("draft", content);
        ResumeDocument document=documents.save(new ResumeDocument(memberId, ResumeDocumentType.GENERATED, title,
                templateFile == null || templateFile.isEmpty() ? null : templateFile.getOriginalFilename(), null, content, metadata, selectedTemplate));
        return ResumeDocumentResponse.from(document);
    }
    public ResumeDocument owned(Long memberId, Long id) { return documents.findByIdAndMemberId(id, memberId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이력서 문서를 찾을 수 없습니다.")); }
    public ResumeDocumentResponse rename(Long memberId, Long id, String title) { if (blank(title)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이력서 이름을 입력해 주세요."); ResumeDocument document = owned(memberId, id); document.rename(title.trim()); return ResumeDocumentResponse.from(document); }
    /** New template fields must also work when a member downloads a draft made before the field was added. */
    public JsonNode templateDataForDownload(Long memberId, ResumeDocument document) {
        JsonNode stored = document.getStructuredContent() == null ? null : document.getStructuredContent().path("templateData");
        if (stored == null || stored.isMissingNode()) return stored;
        ObjectNode data = stored.deepCopy();
        JsonNode personal = resumeEntries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId).stream()
                .filter(entry -> entry.getEntryType() == ResumeEntryType.PERSONAL).map(ResumeEntry::getContent).findFirst().orElse(json.createObjectNode());
        if (blank(data.path("hanjaName").asText())) data.put("hanjaName", personal.path("hanjaName").asText(""));
        if (blank(data.path("birthDate").asText())) data.put("birthDate", personal.path("birthDate").asText(""));
        MemberSpecification currentSpecification = specs.findById(memberId).orElse(null);
        if (currentSpecification != null) {
            // Drafts created before a user attached a photo must still use the latest selected profile photo.
            data.put("profilePhotoDataUrl", photoDataUrl(currentSpecification));
            data.put("portfolioUrl", empty(currentSpecification.getPortfolioUrl()));
            data.put("careerMonths", currentSpecification.getTotalCareerMonths());
        }
        JsonNode existingProjects = data.path("projects");
        List<Map<String, String>> projectItems = projectTemplateItems(memberId, data.path("entries"));
        if (!projectItems.isEmpty()) data.set("projects", json.valueToTree(projectItems));
        else if (!existingProjects.isArray()) data.putArray("projects");
        if (data.path("answers").isArray() && data.path("answers").size() == 1 && data.path("answers").get(0).asText().startsWith("저장된 이력·프로젝트")) data.putArray("answers");
        if (!hasUsableIntroduction(data.path("selfIntroductions"))) data.set("selfIntroductions", json.valueToTree(introductions.findByMemberIdOrderByUpdatedAtDesc(memberId).stream()
                .map(item -> Map.of("title", empty(item.getTitle()), "content", empty(item.getContent()))).toList()));
        return data;
    }
    private boolean hasUsableIntroduction(JsonNode values) { if (!values.isArray()) return false; for (JsonNode value : values) if (!blank(value.path("content").asText())) return true; return false; }
    private List<Map<String, String>> projectTemplateItems(Long memberId, JsonNode entries) {
        List<Project> savedProjects = projects.findByMemberId(memberId);
        if (!savedProjects.isEmpty()) return savedProjects.stream().map(this::projectTemplateItem).toList();
        List<Map<String, String>> inferred = new java.util.ArrayList<>(); JsonNode source = entries != null && entries.isArray() ? entries : entries == null ? json.createArrayNode() : entries.path("entries");
        if (source.isArray()) for (JsonNode entry : source) if ("PORTFOLIO".equals(entry.path("type").asText())) inferred.add(portfolioProjectItem(entry));
        return inferred;
    }
    private Map<String, String> projectTemplateItem(Project project) {
        Map<String, String> item = new java.util.LinkedHashMap<>();
        item.put("title", empty(project.getTitle())); item.put("role", empty(project.getRoleDescription()));
        item.put("problem", empty(project.getProblemDescription())); item.put("solution", empty(project.getSolutionDescription())); item.put("result", empty(project.getResultDescription()));
        item.put("description", joinNonBlank(project.getProblemDescription(), project.getSolutionDescription())); item.put("skills", "");
        item.put("githubUrl", empty(project.getGithubUrl())); item.put("deploymentUrl", empty(project.getDeploymentUrl()));
        item.put("startedAt", project.getStartedAt() == null ? "" : project.getStartedAt().toString()); item.put("endedAt", project.getEndedAt() == null ? "" : project.getEndedAt().toString());
        return item;
    }
    private Map<String, String> portfolioProjectItem(JsonNode entry) {
        JsonNode content = entry.path("content"); String description = content.path("description").asText("");
        Map<String, String> item = new java.util.LinkedHashMap<>();
        item.put("title", entry.path("title").asText("")); item.put("role", lineValue(description, "역할")); item.put("skills", lineValue(description, "기술"));
        item.put("description", description); item.put("problem", ""); item.put("solution", ""); item.put("result", ""); item.put("githubUrl", content.path("url").asText("")); item.put("deploymentUrl", "");
        item.put("startedAt", firstJson(content, "startedAt", "startDate", "projectStartedAt", "from"));
        item.put("endedAt", firstJson(content, "endedAt", "endDate", "projectEndedAt", "to")); return item;
    }
    private String firstJson(JsonNode content, String... keys) { for (String key : keys) { String value = content.path(key).asText("").trim(); if (!value.isBlank()) return value; } return ""; }
    private String lineValue(String text, String label) { if (blank(text)) return ""; Matcher matcher = Pattern.compile("(?m)(?:^|\\n)\\s*" + Pattern.quote(label) + "\\s*[:：]\\s*([^\\n]+)").matcher(text); return matcher.find() ? matcher.group(1).trim() : ""; }
    /**
     * Extract the fields that can be reflected in a member profile without relying
     * on an LLM. This is deliberately a conservative fallback: it only emits a
     * value when the resume actually contains one, instead of copying section
     * headings such as "학력사항" into the profile summary.
     */
    private ObjectNode inferStructured(String source, List<List<String>> tableRows) {
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
        aliases.forEach((alias, canonical) -> { if (containsTerm(lower, alias)) foundSkills.add(canonical); });
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
        parseResumeTables(node, tableRows);
        // Prefer the latest structured academic record. Flat text heuristics are
        // only a fallback for PDFs and non-tabular documents.
        ArrayNode educations = node.withArray("educations");
        if (!educations.isEmpty()) {
            JsonNode latest = educations.get(educations.size() - 1);
            node.put("schoolName", latest.path("school").asText(node.path("schoolName").asText()));
            node.put("major", latest.path("major").asText(node.path("major").asText()));
            node.put("educationLevel", latest.path("degree").asText(node.path("educationLevel").asText()));
            node.put("graduationStatus", latest.path("status").asText(node.path("graduationStatus").asText()));
        }
        node.put("totalCareerMonths", 0);
        // 문서 제목이 반복된 원문을 "추가 설명"으로 저장하지 않는다. 기술은 별도 기술 스택으로만 반영한다.
        node.put("technicalSummary", "");
        return node;
    }

    /** Converts common Korean DOCX resume table rows into the editable entry shape. */
    private void parseResumeTables(ObjectNode node, List<List<String>> rows) {
        ArrayNode educations = node.putArray("educations"); ArrayNode trainings = node.putArray("trainings");
        ArrayNode careers = node.putArray("careers"); ArrayNode awards = node.putArray("awards");
        ArrayNode portfolios = node.putArray("portfolios"); ArrayNode certificates = node.putArray("certificateDetails");
        ArrayNode introductions = node.putArray("selfIntroductions"); String section = "";
        for (List<String> row : rows) {
            if (row.isEmpty()) continue;
            String joined = String.join(" | ", row); String compact = joined.replaceAll("\\s+", "");
            if (compact.contains("성명") && compact.contains("한글")) { ObjectNode personal = (ObjectNode) node.path("personalInfo"); if (row.size() > 2) personal.put("name", row.get(2)); if (row.size() > 4) personal.put("hanjaName", row.get(4)); section = "personal"; continue; }
            if (compact.contains("학교명") && compact.contains("졸업")) { section = "education"; continue; }
            if (compact.contains("교육과정") && compact.contains("교육기관")) { section = "training"; continue; }
            if (compact.contains("최종프로젝트") || compact.contains("중간프로젝트")) { section = "project"; continue; }
            if (compact.contains("근무기간") && compact.contains("회사명")) { section = "career"; continue; }
            if (compact.contains("자격증명") && compact.contains("발행기관")) { section = "certificate"; continue; }
            if (compact.contains("복무기간") && compact.contains("병역사항")) { section = "military"; continue; }
            if (compact.startsWith("성장과정") || compact.startsWith("내가잘할수있는일") || compact.startsWith("습득기술") || compact.startsWith("회사업무")) section = "introduction";
            if ("personal".equals(section)) {
                ObjectNode personal = (ObjectNode) node.path("personalInfo");
                if (compact.contains("생년월일")) putIfFound(personal, "birthDate", findBirthDate(joined));
                else if (compact.toLowerCase(Locale.ROOT).contains("e-mail") || compact.toLowerCase(Locale.ROOT).contains("email")) putIfFound(personal, "email", find(joined, "([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})"));
                else if (compact.contains("휴대폰")) putIfFound(personal, "phone", find(joined, "(01[016789][- ]?\\d{3,4}[- ]?\\d{4})"));
                else if (compact.contains("주소") && row.size() > 1) personal.put("address", row.get(1));
                continue;
            }
            if ("education".equals(section) && row.size() >= 4 && hasPeriod(row.get(0))) {
                ObjectNode value = educations.addObject(); String schoolRaw = row.get(1);
                value.put("school", normalizedSchool(schoolRaw));
                value.put("schoolNote", schoolNote(schoolRaw)); value.put("major", normalizedMajor(row.get(2)));
                value.put("degree", degree(schoolRaw + " " + row.get(3))); value.put("status", graduation(row.get(3)));
                value.put("rawSchool", schoolRaw); value.put("source", "DOCX_TABLE");
                putPeriod(value, row.get(0)); continue;
            }
            if ("training".equals(section) && row.size() >= 3 && hasPeriod(row.get(0))) {
                String course = row.get(1).trim();
                if (isAwardRecord(course)) { ObjectNode value = awards.addObject(); value.put("title", course); value.put("organization", row.get(2).trim()); value.put("description", course); value.put("source", "DOCX_TABLE"); }
                else { ObjectNode value = trainings.addObject(); value.put("title", course); value.put("provider", row.get(2).trim()); value.put("description", course); putPeriod(value, row.get(0)); }
                continue;
            }
            if ("career".equals(section) && row.size() >= 3 && hasPeriod(row.get(0))) {
                ObjectNode value = careers.addObject(); value.put("company", row.get(1).trim()); value.put("description", row.get(2).trim()); if (row.size() > 3) { value.put("position", row.get(3).trim()); value.put("employmentType", employmentType(row.get(3))); } value.put("source", "DOCX_TABLE"); putPeriod(value, row.get(0)); continue;
            }
            if ("certificate".equals(section) && row.size() >= 3 && row.get(0).matches("\\d{2}\\.\\d{2}.*")) {
                ObjectNode value = certificates.addObject(); String rawName = row.get(1).trim(); value.put("acquiredMonth", row.get(0).trim()); value.put("rawName", rawName); value.put("name", rawName); value.put("issuer", canonicalIssuer(row.get(2))); value.put("status", certificateStatus(rawName)); value.put("source", "DOCX_TABLE"); continue;
            }
            if ("military".equals(section) && row.size() >= 3 && hasPeriod(row.get(0))) {
                ObjectNode value = node.putObject("militaryService"); value.put("serviceType", row.get(2).contains("군필") ? "군필" : row.get(2).trim());
                String detail = row.get(1); value.put("rank", firstContained(detail, List.of("이병", "일병", "상병", "병장", "하사", "중사", "상사", "원사")));
                value.put("branch", firstContained(detail, List.of("육군", "해군", "공군", "해병"))); value.put("serviceCategory", firstContained(detail, List.of("현역", "예비역", "보충역", "사회복무요원", "산업기능요원", "전문연구요원"))); value.put("specialty", detail.contains("병") ? detail.substring(detail.lastIndexOf(',') + 1).trim() : ""); value.put("description", detail); value.put("source", "DOCX_TABLE"); if (value.path("branch").asText().isBlank()) value.put("needsReview", true); putPeriod(value, row.get(0)); continue;
            }
            if ("project".equals(section) && row.size() >= 4 && hasPeriod(row.get(0)) && !"내용".equals(row.get(2).trim())) {
                String title = row.get(1).replaceAll("\\s+", " ").trim(); if (!containsTitle(portfolios, title)) {
                    ObjectNode value = portfolios.addObject(); value.put("title", title); value.put("description", "역할: " + row.get(2).trim() + "\n기술: " + row.get(3).trim()); putPeriod(value, row.get(0));
                } continue;
            }
            if ("introduction".equals(section) && row.size() >= 2 && row.get(1).trim().length() > 40) {
                ObjectNode value = introductions.addObject(); value.put("title", row.get(0).replaceAll("\\s+", " ").trim()); value.put("content", row.get(1).trim());
            }
        }
        certificates.forEach(item -> node.withArray("suggestedCertificates").add(item.path("name").asText()));
    }
    private void applyExtractedPhoto(MemberSpecification spec, String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank() || !dataUrl.startsWith("data:image/")) return;
        int comma = dataUrl.indexOf(','); if (comma < 0) return;
        try {
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            String contentType = dataUrl.substring(5, dataUrl.indexOf(';'));
            if (bytes.length <= 2 * 1024 * 1024 && ("image/jpeg".equals(contentType) || "image/png".equals(contentType))) spec.updateProfilePhoto(bytes, contentType);
        } catch (IllegalArgumentException ignored) { }
    }

    private boolean hasPeriod(String value) { return value != null && value.matches(".*\\d{2,4}\\s*\\.\\s*\\d{1,2}.*"); }
    private String schoolNote(String value) { Matcher matcher = Pattern.compile("\\(([^)]+)\\)").matcher(value); if (!matcher.find()) return ""; String note = matcher.group(1).trim(); return note.matches(".*(전공심화|[234]년제|주간|야간|편입).*" ) ? note : ""; }
    private String normalizedSchool(String value) { String note = schoolNote(value); return note.isBlank() ? value.trim() : value.replaceFirst("\\s*\\(" + Pattern.quote(note) + "\\)", "").trim(); }
    private String normalizedMajor(String value) { return value == null ? "" : value.replaceAll("\\s+", " ").replaceAll("\\s*(학과|학부)$", "$1").trim(); }
    private boolean isAwardRecord(String value) { return value != null && Pattern.compile("(^|\\s)(대상|최우수상|우수상|장려상|공로상|모범상|표창|수상)(\\s|$|\\()|상$|표창$").matcher(value.trim()).find(); }
    private String employmentType(String value) { String raw = value == null ? "" : value; if (raw.contains("아르바이트") || raw.contains("알바")) return "아르바이트"; if (raw.contains("인턴")) return "인턴"; if (raw.contains("계약")) return "계약직"; if (raw.contains("프리랜서")) return "프리랜서"; if (raw.contains("정규")) return "정규직"; return "미확인"; }
    private String degree(String value) { if (value.contains("박사") || value.contains("석사") || value.contains("대학원")) return "대학원"; if (value.contains("전문학사") || value.contains("전문대")) return "전문대"; if (value.contains("학사") || value.contains("대학교") || value.matches(".*\\b대학\\b.*")) return "대학교"; return value.contains("고등") || value.contains("고졸") ? "고등학교" : ""; }
    private String graduation(String value) { String raw = value == null ? "" : value.replaceAll("\\s+", ""); if (raw.contains("졸업예정") || raw.equalsIgnoreCase("EXPECTED")) return "졸업 예정"; if (raw.contains("졸업") || raw.equalsIgnoreCase("GRADUATED")) return "졸업"; if (raw.contains("재학") || raw.equalsIgnoreCase("ENROLLED")) return "재학"; if (raw.contains("휴학")) return "휴학"; if (raw.contains("수료")) return "수료"; if (raw.contains("중퇴")) return "중퇴"; return ""; }
    private String firstContained(String value, List<String> options) { return options.stream().filter(value::contains).findFirst().orElse(""); }
    private void putPeriod(ObjectNode target, String period) { Matcher matcher = Pattern.compile("(\\d{2,4})\\s*\\.\\s*(\\d{1,2}).*?[~\\-](\\d{2,4})\\s*\\.\\s*(\\d{1,2})").matcher(period); if (matcher.find()) { String start = monthDate(matcher.group(1), matcher.group(2)); String end = monthDate(matcher.group(3), matcher.group(4)); if (!start.isBlank() && !end.isBlank() && !LocalDate.parse(start).isAfter(LocalDate.parse(end))) { target.put("startedAt", start); target.put("endedAt", end); } else target.put("needsReview", true); } }
    private String monthDate(String year, String month) { int value = Integer.parseInt(year); int monthValue = Integer.parseInt(month); if (monthValue < 1 || monthValue > 12) return ""; if (value < 100) { int current = LocalDate.now().getYear() % 100; value += value <= current + 5 ? 2000 : 1900; } return String.format("%04d-%02d-01", value, monthValue); }
    private void classifyCareerRelevance(ObjectNode extracted, String targetContext) {
        ArrayNode careers = extracted.withArray("careers");
        for (JsonNode career : careers) if (career instanceof ObjectNode value) {
            String evidence = String.join(" ", value.path("company").asText(), value.path("position").asText(), value.path("description").asText());
            boolean relevant = isRelevantCareer(evidence, targetContext);
            value.put("relevantCareer", relevant);
            if (!relevant) value.put("exclusionReason", "지원 직무와 연결되는 업무 근거가 없어 관련 경력에서 제외");
        }
        extracted.put("totalCareerMonths", careerMonths(careers));
        extracted.put("careerMonthsPolicy", "JOB_RELEVANT");
    }
    private void normalizeCertificateSuggestions(ObjectNode extracted) {
        ArrayNode details = extracted.withArray("certificateDetails");
        ArrayNode suggestions = extracted.withArray("suggestedCertificates");
        String driverKind = firstDriverLicenseKind(details); if (driverKind.isBlank()) driverKind = firstDriverLicenseKind(suggestions);
        String contextKind = driverKind;
        for (JsonNode detail : details) if (detail instanceof ObjectNode value) { String raw = value.path("rawName").asText(value.path("name").asText()); value.put("name", canonicalCertificateName(raw, contextKind)); value.put("status", certificateStatus(raw)); }
        Set<String> unique = new java.util.LinkedHashSet<>();
        suggestions.forEach(value -> { String name = canonicalCertificateName(value.asText(), contextKind); if (!name.isBlank()) unique.add(name); });
        details.forEach(value -> { String name = canonicalCertificateName(value.path("name").asText(), contextKind); if (!name.isBlank()) unique.add(name); });
        suggestions.removeAll(); unique.forEach(suggestions::add);
    }
    private void normalizeStructuredProfile(ObjectNode extracted) {
        for (JsonNode item : extracted.withArray("educations")) if (item instanceof ObjectNode value) {
            String rawSchool = value.path("rawSchool").asText(value.path("school").asText()); value.put("rawSchool", rawSchool); value.put("school", normalizedSchool(rawSchool));
            value.put("schoolNote", schoolNote(rawSchool)); value.put("major", normalizedMajor(value.path("major").asText())); value.put("degree", degree(rawSchool + " " + value.path("degree").asText())); value.put("status", graduation(value.path("status").asText()));
            normalizeDateField(value, "startedAt"); normalizeDateField(value, "endedAt"); if (value.path("school").asText().isBlank() || value.path("degree").asText().isBlank()) value.put("needsReview", true);
        }
        for (String array : List.of("careers", "trainings", "portfolios")) for (JsonNode item : extracted.withArray(array)) if (item instanceof ObjectNode value) { normalizeDateField(value, "startedAt"); normalizeDateField(value, "endedAt"); }
        for (JsonNode item : extracted.withArray("awards")) if (item instanceof ObjectNode value) normalizeDateField(value, "awardedAt");
        ObjectNode personal = extracted.path("personalInfo") instanceof ObjectNode value ? value : extracted.putObject("personalInfo");
        if (!personal.path("email").asText().isBlank()) personal.put("email", personal.path("email").asText().trim().toLowerCase(Locale.ROOT));
        if (!personal.path("phone").asText().isBlank()) personal.put("phone", normalizedPhone(personal.path("phone").asText()));
        deduplicate(extracted.withArray("educations"), List.of("school", "degree", "startedAt", "endedAt"));
        deduplicate(extracted.withArray("careers"), List.of("company", "position", "startedAt", "endedAt"));
        deduplicate(extracted.withArray("trainings"), List.of("provider", "title", "startedAt", "endedAt"));
        deduplicate(extracted.withArray("awards"), List.of("organization", "title", "awardedAt"));
        deduplicate(extracted.withArray("portfolios"), List.of("title", "startedAt", "endedAt"));
        deduplicate(extracted.withArray("selfIntroductions"), List.of("title", "content"));
    }
    private void normalizeDateField(ObjectNode value, String key) { String raw = value.path(key).asText("").trim(); if (raw.isBlank()) return; Matcher matcher = Pattern.compile("(\\d{2,4})[.\\-/년\\s]+(\\d{1,2})(?:[.\\-/월\\s]+(\\d{1,2}))?").matcher(raw); if (!matcher.find()) { value.put("needsReview", true); return; } String month = monthDate(matcher.group(1), matcher.group(2)); if (month.isBlank()) { value.put("needsReview", true); return; } value.put(key, matcher.group(3) == null ? month : month.substring(0, 8) + String.format("%02d", Math.clamp(Integer.parseInt(matcher.group(3)), 1, 28))); }
    private void deduplicate(ArrayNode values, List<String> keys) { Set<String> seen = new HashSet<>(); for (int index = values.size() - 1; index >= 0; index--) { JsonNode value = values.get(index); String identity = keys.stream().map(key -> normalize(value.path(key).asText())).collect(Collectors.joining("|")); if (identity.replace("|", "").isBlank() || !seen.add(identity)) values.remove(index); } }
    private String normalizedPhone(String value) { String digits = value == null ? "" : value.replaceAll("\\D", ""); if (digits.startsWith("82")) digits = "0" + digits.substring(2); if (digits.matches("01\\d{8}")) return digits.replaceFirst("(\\d{3})(\\d{3})(\\d{4})", "$1-$2-$3"); if (digits.matches("01\\d{9}")) return digits.replaceFirst("(\\d{3})(\\d{4})(\\d{4})", "$1-$2-$3"); return value.trim(); }
    private boolean isRelevantCareer(String evidence, String targetContext) {
        String normalized = evidence.toLowerCase(Locale.ROOT);
        boolean itTarget = targetContext.isBlank() || List.of("개발", "프로그래", "소프트웨어", "데이터", "ai", "인공지능", "it", "서버", "웹", "앱", "클라우드", "보안", "네트워크", "인프라", "devops")
                .stream().anyMatch(keyword -> targetContext.toLowerCase(Locale.ROOT).contains(keyword));
        if (itTarget) return List.of("개발", "프로그래", "소프트웨어", "데이터", "ai", "인공지능", "서버", "웹", "앱", "클라우드", "보안", "네트워크", "인프라", "devops", "java", "python", "spring", "react")
                .stream().anyMatch(normalized::contains);
        return java.util.Arrays.stream(targetContext.toLowerCase(Locale.ROOT).split("[^가-힣a-z0-9+#.]+"))
                .filter(word -> word.length() >= 2).anyMatch(word -> evidence.toLowerCase(Locale.ROOT).contains(word));
    }
    private int careerMonths(ArrayNode careers) { int total = 0; for (JsonNode career : careers) { if (!career.path("relevantCareer").asBoolean(false)) continue; try { LocalDate start = LocalDate.parse(career.path("startedAt").asText()); LocalDate end = LocalDate.parse(career.path("endedAt").asText()); total += Math.max(0, (end.getYear() - start.getYear()) * 12 + end.getMonthValue() - start.getMonthValue()); } catch (Exception ignored) { } } return total; }
    private boolean containsTitle(ArrayNode values, String title) { for (JsonNode value : values) if (title.equals(value.path("title").asText())) return true; return false; }

    private boolean containsTerm(String text, String term) {
        if (blank(term)) return false;
        String candidate = term.toLowerCase(Locale.ROOT).trim();
        return Pattern.compile("(?<![a-z0-9가-힣+#])" + Pattern.quote(candidate) + "(?![a-z0-9가-힣+#])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
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
            for (String key : List.of("educations", "careers", "trainings", "awards", "portfolios", "certificateDetails", "selfIntroductions")) mergeObjectArray(extracted.withArray(key), profile.get(key));
            if (extracted.path("militaryService").isMissingNode() || extracted.path("militaryService").isEmpty()) {
                JsonNode military = json.valueToTree(profile.get("militaryService")); if (military.isObject() && !military.isEmpty()) extracted.set("militaryService", military);
            }
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
    private String preserve(String current, String extracted) { return blank(current) ? empty(extracted) : current; }
    private String templateKey(String value) {
        if ("ACADEMY".equalsIgnoreCase(value)) return "ACADEMY";
        if ("SARAMIN".equalsIgnoreCase(value)) return "SARAMIN";
        if ("JOBKOREA".equalsIgnoreCase(value)) return "JOBKOREA";
        if ("PROJECT".equalsIgnoreCase(value)) return "PROJECT";
        if ("COMPACT".equalsIgnoreCase(value)) return "COMPACT";
        return "STANDARD";
    }
    private String templateTitle(String template) { return switch (template) { case "ACADEMY" -> "개발교육원형 이력서 초안"; case "SARAMIN" -> "사람인형 이력서 초안"; case "JOBKOREA" -> "잡코리아형 이력서 초안"; default -> "Job-A-Dream 이력서 초안"; }; }
    private String builtInTemplateHint(String template) {
        return switch (template) {
            case "ACADEMY" -> "개발교육원형: 인적사항(사진 포함), 학력사항, 교육사항, 프로젝트 요약 2건, 기술 환경, 경력사항, 자격·면허, 활동, 자기소개, 프로젝트 상세 2건 순서. 각 칸에는 저장된 사실만 넣고 빈칸은 비워 둔다.";
            case "SARAMIN" -> "사람인형: 기본사항, 학력사항, 경력사항, 인턴·사회봉사, OA·외국어 등 기능사항 순서. 저장 사실이 없는 칸은 비워 둔다.";
            case "JOBKOREA" -> "잡코리아형: 인적사항, 학력사항, 경력사항, 어학, 교육·연수, 기타활동 순서. 프로젝트는 기타활동 또는 경력의 실제 문장으로 간결히 정리한다.";
            default -> "지원 직무, 핵심 역량, 학력 및 경력, 프로젝트 및 경험, 자기소개 순서";
        };
    }
    private void applyExtractedSkills(Long memberId, JsonNode candidates) {
        if (!candidates.isArray()) return;
        Set<Long> owned = memberSkills.findByMemberId(memberId).stream().map(MemberSkill::getSkillId).collect(Collectors.toSet());
        List<MemberSkill> additions = new java.util.ArrayList<>();
        for (JsonNode candidate : candidates) {
            if (owned.size() >= MAX_PROFILE_SKILLS) break;
            String name = candidate.asText("").trim();
            skillCatalog.findByName(name).filter(Skill::isCanonical).filter(skill -> !owned.contains(skill.getId()))
                    .ifPresent(skill -> { additions.add(new MemberSkill(memberId, skill.getId(), "LEARNING", "이력서에서 추출")); owned.add(skill.getId()); });
        }
        if (!additions.isEmpty()) memberSkills.saveAll(additions);
    }
    private void annotateSkillImport(Long memberId, ObjectNode extracted, String sourceText) {
        Set<Long> owned = memberSkills.findByMemberId(memberId).stream().map(MemberSkill::getSkillId).collect(Collectors.toSet());
        int available = Math.max(0, MAX_PROFILE_SKILLS - owned.size());
        List<Skill> candidates = new java.util.ArrayList<>(); Set<Long> seen = new HashSet<>();
        for (JsonNode item : extracted.path("suggestedSkills")) skillCatalog.findByName(item.asText("").trim())
                .filter(Skill::isCanonical).filter(skill -> !owned.contains(skill.getId()) && seen.add(skill.getId())).ifPresent(candidates::add);
        String lower = sourceText == null ? "" : sourceText.toLowerCase(Locale.ROOT);
        candidates.sort(java.util.Comparator.comparingInt((Skill skill) -> occurrences(lower, skill.getName().toLowerCase(Locale.ROOT))).reversed().thenComparing(Skill::getName));
        ArrayNode selected = extracted.putArray("autoSelectedSkills"); ArrayNode remaining = extracted.putArray("additionalSkillCandidates");
        for (int index = 0; index < candidates.size(); index++) (index < available ? selected : remaining).add(candidates.get(index).getName());
        extracted.put("skillProfileLimit", MAX_PROFILE_SKILLS); extracted.put("currentProfileSkillCount", owned.size());
    }
    private int occurrences(String text, String term) { int count = 0; for (int index = text.indexOf(term); index >= 0; index = text.indexOf(term, index + term.length())) count++; return count; }
    private void applyExtractedCertificates(Long memberId, JsonNode details, JsonNode candidates) {
        normalizeExistingDriverLicense(memberId, details);
        Set<String> owned = certificates.findByMemberId(memberId).stream().map(Certificate::getName).map(this::normalize).collect(Collectors.toSet());
        List<Certificate> additions = new java.util.ArrayList<>();
        boolean structured = details.isArray() && !details.isEmpty();
        if (structured) for (JsonNode detail : details) {
            String name = canonicalCertificateName(detail.path("name").asText(""), firstDriverLicenseKind(details));
            // 합격·예정·접수 상태는 검토 정보이며 취득 자격증으로 저장하지 않는다.
            if (!name.isBlank() && "취득".equals(detail.path("status").asText("취득")) && owned.add(normalize(name))) additions.add(new Certificate(memberId, name, canonicalIssuer(detail.path("issuer").asText("이력서 추출")), parseMonth(detail.path("acquiredMonth").asText()), null, null));
        }
        if (!structured && candidates.isArray()) for (JsonNode candidate : candidates) {
            String name = canonicalCertificateName(candidate.asText(""), firstDriverLicenseKind(candidates));
            if (!name.isBlank() && owned.add(normalize(name))) additions.add(new Certificate(memberId, name, "이력서 추출", null, null, null));
        }
        if (!additions.isEmpty()) certificates.saveAll(additions);
    }
    private void mergeObjectArray(ArrayNode target, Object values) { if (!(values instanceof List<?> list)) return; for (Object value : list) { JsonNode node = json.valueToTree(value); if (node.isObject() && !node.isEmpty()) target.add(node); } }
    private void normalizeExistingDriverLicense(Long memberId, JsonNode details) {
        String driverKind = firstDriverLicenseKind(details); if (driverKind.isBlank()) return;
        List<Certificate> aliases = certificates.findByMemberId(memberId).stream().filter(item -> { String kind = driverLicenseKind(item.getName()); return isGenericDriverLicense(item.getName()) || driverKind.equals(kind); }).toList();
        if (aliases.isEmpty()) return;
        JsonNode detail = details.isArray() ? java.util.stream.StreamSupport.stream(details.spliterator(), false)
                .filter(item -> driverKind.equals(driverLicenseKind(item.path("name").asText()))).findFirst().orElse(null) : null;
        Certificate keeper = aliases.stream().filter(item -> driverKind.equals(driverLicenseKind(item.getName()))).findFirst().orElse(aliases.get(0));
        keeper.normalizeImportedIdentity("자동차운전면허 " + driverKind, detail == null ? keeper.getIssuer() : canonicalIssuer(detail.path("issuer").asText(keeper.getIssuer())),
                detail == null ? keeper.getAcquiredAt() : parseMonth(detail.path("acquiredMonth").asText()));
        certificates.save(keeper);
        List<Certificate> duplicates = aliases.stream().filter(item -> !item.getId().equals(keeper.getId())).toList();
        if (!duplicates.isEmpty()) certificates.deleteAll(duplicates);
    }
    private String canonicalCertificateName(String value, String driverContext) {
        String raw = value == null ? "" : value.trim(); String compact = normalize(raw); String driverKind = driverLicenseKind(raw);
        if (!driverKind.isBlank()) return "자동차운전면허 " + driverKind;
        if (isGenericDriverLicense(raw) && !driverContext.isBlank()) return "자동차운전면허 " + driverContext;
        if (isGenericDriverLicense(raw)) return "자동차운전면허 (종류 미확인)";
        String statusRemoved = raw.replaceAll("\\s*(필기|실기|최종)?\\s*(합격|취득예정|응시예정|접수|만료|갱신필요)\\s*$", "").trim(); compact = normalize(statusRemoved);
        if (compact.startsWith("itq")) { if (compact.contains("파워포인트")) return "ITQ 한글파워포인트"; if (compact.contains("엑셀")) return "ITQ 한글엑셀"; if (compact.contains("아래한글") || compact.contains("한글")) return "ITQ 아래한글"; if (compact.contains("인터넷")) return "ITQ 인터넷"; return "ITQ (종목 미확인)"; }
        if (compact.startsWith("gtq")) { Matcher grade = Pattern.compile("([123])급").matcher(compact); return "GTQ 그래픽기술자격" + (grade.find() ? " " + grade.group(1) + "급" : ""); }
        if (compact.contains("컴퓨터활용능력") || compact.startsWith("컴활")) { Matcher grade = Pattern.compile("([12])급").matcher(compact); return "컴퓨터활용능력" + (grade.find() ? " " + grade.group(1) + "급" : ""); }
        if (compact.equals("sqld") || compact.equals("sql개발자")) return "SQLD"; if (compact.equals("sqlp") || compact.equals("sql전문가")) return "SQLP";
        if (compact.equals("opic") || compact.equals("오픽")) return "OPIc"; if (compact.equals("toeic") || compact.equals("토익")) return "TOEIC";
        return statusRemoved;
    }
    private String driverLicenseKind(String value) { String compact = normalize(value).replace("제", ""); if (compact.contains("대형견인")) return "1종 특수 대형견인"; if (compact.contains("소형견인")) return "1종 특수 소형견인"; if (compact.contains("구난")) return "1종 특수 구난"; if (compact.contains("1종대형") || compact.equals("대형면허")) return "1종 대형"; if (compact.contains("1종보통") || compact.equals("보통1종")) return "1종 보통"; if (compact.contains("2종소형")) return "2종 소형"; if (compact.contains("2종보통") || compact.equals("보통2종")) return "2종 보통"; if (compact.contains("원동기")) return "원동기장치자전거"; return ""; }
    private String firstDriverLicenseKind(JsonNode values) { if (!values.isArray()) return ""; for (JsonNode value : values) { String name = value.isObject() ? value.path("rawName").asText(value.path("name").asText()) : value.asText(); String kind = driverLicenseKind(name); if (!kind.isBlank()) return kind; } return ""; }
    private boolean isGenericDriverLicense(String value) { String compact = normalize(value); return compact.equals("운전면허") || compact.equals("자동차운전면허"); }
    private String certificateStatus(String value) { String raw = value == null ? "" : value.replaceAll("\\s+", ""); if (raw.contains("필기합격")) return "필기 합격"; if (raw.contains("실기합격")) return "실기 합격"; if (raw.contains("최종합격")) return "최종 합격"; if (raw.contains("취득예정")) return "취득 예정"; if (raw.contains("응시예정")) return "응시 예정"; if (raw.contains("접수")) return "접수"; if (raw.contains("만료")) return "만료"; return "취득"; }
    private String canonicalIssuer(String value) { String raw = value == null ? "" : value.trim(); String compact = normalize(raw); if (compact.equals("산업인력공단") || compact.equals("hrdk") || compact.contains("한국산업인력공단")) return "한국산업인력공단"; if (compact.equals("kpc") || compact.contains("한국생산성본부")) return "한국생산성본부"; if (compact.contains("도로교통공단")) return "도로교통공단"; if (compact.equals("k-data") || compact.contains("한국데이터산업진흥원")) return "한국데이터산업진흥원"; if (compact.contains("대한상공회의소") || compact.equals("상공회의소")) return "대한상공회의소"; return raw; }
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
            if (!value.isBlank() && content.path(key).asText("").isBlank()) content.put(key, value);
        }
        if (existing == null) resumeEntries.save(new ResumeEntry(memberId, ResumeEntryType.PERSONAL, "인적사항", content, 0));
        else existing.update("인적사항", content, existing.getDisplayOrder());
    }
    private void applyStructuredEntries(Long memberId, JsonNode data) {
        List<ResumeEntry> existing = resumeEntries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId);
        appendEntries(memberId, existing, ResumeEntryType.EDUCATION, data.path("educations"), "school");
        appendRelevantCareers(memberId, existing, data.path("careers"));
        appendEntries(memberId, existing, ResumeEntryType.TRAINING, data.path("trainings"), "title");
        appendEntries(memberId, existing, ResumeEntryType.AWARD, data.path("awards"), "title");
        appendEntries(memberId, existing, ResumeEntryType.PORTFOLIO, data.path("portfolios"), "title");
        JsonNode military = data.path("militaryService");
        if (military.isObject() && existing.stream().noneMatch(entry -> entry.getEntryType() == ResumeEntryType.PREFERENCE)) resumeEntries.save(new ResumeEntry(memberId, ResumeEntryType.PREFERENCE, "병역사항", military, 0));
        if (data.path("selfIntroductions").isArray()) for (JsonNode intro : data.path("selfIntroductions")) {
            String title = intro.path("title").asText("").trim(); String content = intro.path("content").asText("").trim();
            if (!title.isBlank() && !content.isBlank() && introductions.findByMemberIdOrderByUpdatedAtDesc(memberId).stream().noneMatch(item -> normalize(item.getTitle()).equals(normalize(title)) && normalize(item.getContent()).equals(normalize(content)))) introductions.save(new SelfIntroduction(memberId, title, content, false));
        }
    }
    private void appendEntries(Long memberId, List<ResumeEntry> existing, ResumeEntryType type, JsonNode values, String titleKey) {
        if (!values.isArray()) return; int order = (int) existing.stream().filter(entry -> entry.getEntryType() == type).count();
        for (JsonNode value : values) { String title = value.path(titleKey).asText("").trim();
            String identity = entryIdentity(type, title, value); if (!title.isBlank() && existing.stream().noneMatch(entry -> entry.getEntryType() == type && entryIdentity(type, entry.getTitle(), entry.getContent()).equals(identity))) { ResumeEntry saved = resumeEntries.save(new ResumeEntry(memberId, type, title, value, order++)); existing.add(saved); }
        }
    }
    private void appendRelevantCareers(Long memberId, List<ResumeEntry> existing, JsonNode values) {
        if (!values.isArray()) return; int order = (int) existing.stream().filter(entry -> entry.getEntryType() == ResumeEntryType.CAREER).count();
        for (JsonNode value : values) { if (!value.path("relevantCareer").asBoolean(false)) continue; String title = value.path("company").asText("").trim();
            String identity = entryIdentity(ResumeEntryType.CAREER, title, value); if (!title.isBlank() && existing.stream().noneMatch(entry -> entry.getEntryType() == ResumeEntryType.CAREER && entryIdentity(ResumeEntryType.CAREER, entry.getTitle(), entry.getContent()).equals(identity))) { ResumeEntry saved = resumeEntries.save(new ResumeEntry(memberId, ResumeEntryType.CAREER, title, value, order++)); existing.add(saved); }
        }
    }
    private String entryIdentity(ResumeEntryType type, String title, JsonNode value) { List<String> keys = switch (type) { case EDUCATION -> List.of("school", "degree", "startedAt", "endedAt"); case CAREER -> List.of("company", "position", "startedAt", "endedAt"); case TRAINING -> List.of("provider", "title", "startedAt", "endedAt"); case AWARD -> List.of("organization", "title", "awardedAt"); case PORTFOLIO -> List.of("title", "startedAt", "endedAt"); default -> List.of(); }; return normalize(title) + "|" + keys.stream().map(key -> normalize(value.path(key).asText())).collect(Collectors.joining("|")); }
    private LocalDate parseMonth(String value) { Matcher matcher = Pattern.compile("(\\d{2,4})\\s*\\.\\s*(\\d{1,2})").matcher(value == null ? "" : value); if (!matcher.find()) return null; String normalized = monthDate(matcher.group(1), matcher.group(2)); return normalized.isBlank() ? null : LocalDate.parse(normalized); }
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
    private String careerText(int months) { return (months / 12 > 0 ? months / 12 + "년 " : "") + (months % 12) + "개월"; }
    private String shorten(String value, int limit) { String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim(); return normalized.length() > limit ? normalized.substring(0, limit) + "…" : normalized; }
    private String nonBlankSuffix(String value) { return blank(value) ? "" : " · " + shorten(value, 120); }
    private String entrySummary(JsonNode value) { if (value == null || value.isMissingNode()) return ""; String detail = String.join(" · ", List.of(value.path("company").asText(), value.path("school").asText(), value.path("major").asText(), value.path("description").asText()).stream().filter(item -> !blank(item)).toList()); return blank(detail) ? "" : " · " + shorten(detail, 120); }
    private String merge(String current,String extracted){ return blank(current)?extracted:(blank(extracted)?current:current+"\n"+extracted); }
    private String photoDataUrl(MemberSpecification specification) { return specification.getProfilePhoto() == null || blank(specification.getProfilePhotoContentType()) ? "" : "data:" + specification.getProfilePhotoContentType() + ";base64," + Base64.getEncoder().encodeToString(specification.getProfilePhoto()); }
    private String empty(String v){return v==null?"":v;} private boolean blank(String v){return v==null||v.isBlank();} private String value(String v,String fallback){return blank(v)?fallback:v;} private String joinNonBlank(String a,String b){return (empty(a)+" "+empty(b)).trim();}
}

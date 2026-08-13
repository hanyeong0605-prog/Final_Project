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
import java.util.List;
import java.util.Locale;
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
    private final SelfIntroductionRepository introductions; private final ObjectMapper json; private final JobMatchRefreshScheduler refreshScheduler;
    public ResumeDocumentService(ResumeDocumentRepository documents, ResumeDocumentTextExtractor extractor, MemberRepository members,
        MemberProfileRepository profiles, MemberSpecificationRepository specs, MemberSkillRepository memberSkills, SkillRepository skillCatalog, CertificateRepository certificates,
        ProjectRepository projects, SelfIntroductionRepository introductions, ObjectMapper json, JobMatchRefreshScheduler refreshScheduler) {
        this.documents=documents; this.extractor=extractor; this.members=members; this.profiles=profiles; this.specs=specs; this.memberSkills=memberSkills;
        this.skillCatalog=skillCatalog; this.certificates=certificates; this.projects=projects; this.introductions=introductions; this.json=json; this.refreshScheduler=refreshScheduler;
    }
    public List<ResumeDocumentResponse> list(Long memberId) { return documents.findByMemberIdOrderByCreatedAtDesc(memberId).stream().map(ResumeDocumentResponse::from).toList(); }
    public ResumeDocumentResponse extract(Long memberId, MultipartFile file) {
        String text = extractor.extract(file);
        ObjectNode extracted = infer(text);
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
        profiles.save(profile); specs.save(spec); member.completeOnboarding(); refreshScheduler.enqueueForMember(memberId);
        return ResumeDocumentResponse.from(document);
    }
    public ResumeDocumentResponse generate(Long memberId, ResumeDraftRequest request) {
        MemberProfile profile=profiles.findById(memberId).orElse(null); MemberSpecification spec=specs.findById(memberId).orElse(null);
        String skillList=memberSkills.findByMemberId(memberId).stream()
                .map(v -> skillCatalog.findById(v.getSkillId()).map(Skill::getName).orElse(v.getNote()))
                .filter(v -> !blank(v)).collect(Collectors.joining(", "));
        String certList=certificates.findByMemberId(memberId).stream().map(Certificate::getName).collect(Collectors.joining(", "));
        String projectList=projects.findByMemberId(memberId).stream().map(Project::getTitle).collect(Collectors.joining(", "));
        String intro=introductions.findByMemberIdOrderByUpdatedAtDesc(memberId).stream().map(SelfIntroduction::getContent).findFirst().orElse("");
        String title=blank(request.title()) ? "Job-A-Dream 이력서 초안" : request.title().trim();
        String content = "# " + title + "\n\n## 지원 직무\n" + value(profile == null ? null : profile.getTargetRole(), "지원 직무를 입력해 주세요")
          + "\n\n## 핵심 역량\n" + value(skillList, "보유 기술을 추가해 주세요")
          + "\n\n## 학력 및 경력\n" + value(spec == null ? null : joinNonBlank(spec.getEducationLevel(), spec.getMajor()), "학력 정보를 입력해 주세요")
          + "\n경력 " + (spec == null ? 0 : spec.getTotalCareerMonths()) + "개월\n\n## 자격증\n" + value(certList, "보유 자격증을 추가해 주세요")
          + "\n\n## 프로젝트\n" + value(projectList, "프로젝트 경험을 추가해 주세요")
          + "\n\n## 자기소개\n" + value(intro, "지원 직무와 연결되는 경험을 STAR 방식으로 작성해 주세요")
          + (blank(request.additionalRequest()) ? "" : "\n\n## 작성 요청 반영\n" + request.additionalRequest().trim());
        ResumeDocument document=documents.save(new ResumeDocument(memberId, ResumeDocumentType.GENERATED, title, null, null, content, null));
        return ResumeDocumentResponse.from(document);
    }
    public ResumeDocument owned(Long memberId, Long id) { return documents.findByIdAndMemberId(id, memberId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이력서 문서를 찾을 수 없습니다.")); }
    private ObjectNode infer(String text) {
        String lower=text.toLowerCase(Locale.ROOT); ObjectNode node=json.createObjectNode(); ArrayNode skills=node.putArray("suggestedSkills");
        for (String value: List.of("Java","Spring","React","TypeScript","JavaScript","Python","AWS","Docker","SQL","MySQL","Git")) if (lower.contains(value.toLowerCase(Locale.ROOT))) skills.add(value);
        ArrayNode certs=node.putArray("suggestedCertificates"); for (String value: List.of("정보처리기사","SQLD","ADsP","AWS")) if (text.contains(value)) certs.add(value);
        node.put("educationLevel", text.contains("대학교") || text.contains("학사") ? "BACHELOR" : "");
        node.put("major", keywordAfter(text, "학과", "전공")); node.put("targetRole", firstRole(text));
        node.put("totalCareerMonths", inferCareerMonths(text)); node.put("technicalSummary", summarize(text)); return node;
    }
    private String firstRole(String text) { for(String v:List.of("백엔드 개발자","프론트엔드 개발자","풀스택 개발자","데이터 엔지니어","AI 엔지니어","개발자")) if(text.contains(v)) return v; return ""; }
    private int inferCareerMonths(String text) { var m=java.util.regex.Pattern.compile("(\\d+)\\s*년").matcher(text); return m.find() ? Integer.parseInt(m.group(1))*12 : 0; }
    private String keywordAfter(String text,String... keys){ for(String k:keys){int i=text.indexOf(k); if(i>0)return text.substring(Math.max(0,i-24),Math.min(text.length(),i+k.length())).replaceAll("[\\r\\n]+"," ").trim();} return ""; }
    private String summarize(String text){ return text.replaceAll("\\s+"," ").trim().substring(0, Math.min(800, text.replaceAll("\\s+"," ").trim().length())); }
    private String first(JsonNode node,String field,String fallback){String v=node.path(field).asText(""); return blank(v)?fallback:v;}
    private String merge(String current,String extracted){ return blank(current)?extracted:(blank(extracted)?current:current+"\n"+extracted); }
    private String empty(String v){return v==null?"":v;} private boolean blank(String v){return v==null||v.isBlank();} private String value(String v,String fallback){return blank(v)?fallback:v;} private String joinNonBlank(String a,String b){return (empty(a)+" "+empty(b)).trim();}
}

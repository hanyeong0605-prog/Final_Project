package com.jobpilot.api.domain.matching.service;

import com.jobpilot.api.domain.matching.dto.GrowthActionResponse;
import com.jobpilot.api.domain.matching.dto.JobMatchDetailResponse;
import com.jobpilot.api.domain.matching.dto.JobMatchEvidenceResponse;
import com.jobpilot.api.domain.matching.dto.GrowthResourceRecommendationResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class JobMatchGrowthActionService {
    private final JobMatchService matches;
    private final RequirementLearningResourceService learningResources;
    public JobMatchGrowthActionService(JobMatchService matches, RequirementLearningResourceService learningResources) { this.matches = matches; this.learningResources = learningResources; }
    public List<GrowthActionResponse> forMatch(Long memberId, Long jobPostingId) {
        JobMatchDetailResponse detail = matches.findDetail(memberId, jobPostingId);
        LinkedHashMap<String, ActionGroup> groups = new LinkedHashMap<>();
        for (JobMatchEvidenceResponse evidence : detail.evidences()) {
            if (!"MISSING".equals(evidence.status())) continue;
            String requirement = value(evidence.requirement()); String type = value(evidence.requirementType()).toUpperCase(Locale.ROOT);
            if (!(type.equals("SKILL") || type.equals("EXPERIENCE") || type.equals("CERTIFICATION"))) continue;
            if (!type.equals("CERTIFICATION") && learningKeyword(requirement).isBlank()) continue;
            ActionTemplate template = templateFor(type, requirement);
            groups.computeIfAbsent(template.category() + "|" + template.title() + "|" + learningKeyword(requirement), ignored -> new ActionGroup(template, type))
                    .add(evidence.requirementId(), requirement);
        }
        return groups.values().stream().map(group -> group.toResponse(learningResources)).filter(java.util.Objects::nonNull).toList();
    }
    private ActionTemplate templateFor(String type, String requirement) {
        if (type.contains("CERT")) return new ActionTemplate("자격증", certificate(requirement), "요구 요건과 연결되는 국가·민간 자격 종목을 찾아 취득 계획을 세워보세요.", "자격증 검색에서 종목을 확인하고 취득 예정일을 역량 프로필에 기록하세요.", "/profile");
        if (type.contains("SKILL") && learningKeyword(requirement).equals("RAG")) return new ActionTemplate("기술 스택", "RAG 기술 스택 학습을 통해 성장하기", "공고가 요구하는 RAG 기술을 증명할 프로젝트·기술 스택 근거가 아직 없습니다. 검색, 임베딩, 벡터 DB, 평가 과정을 결과물로 남겨 보강하세요.", "RAG 학습 후 질의응답 미니 프로젝트를 만들고, 사용 기술과 평가 결과를 포트폴리오에 기록하세요.", "/opportunities");
        if (type.contains("SKILL")) return new ActionTemplate("실습·강의", "요구 기술 미니 프로젝트 만들기", "강의 수강만으로 끝내지 말고, 해당 기술을 사용한 결과물 1개를 포트폴리오에 남기세요.", "성장 기회 추천에서 관련 교육·부트캠프·공모전을 찾아보세요.", "/opportunities");
        if (type.contains("EXPERIENCE")) return new ActionTemplate("프로젝트", "경험을 증명할 프로젝트 설계", "요구 경험을 작은 과제로 쪼개어 역할·문제·해결·결과를 남기는 방식으로 보강하세요.", "이력서 작성 도우미에서 프로젝트 경험을 STAR 형식으로 정리하세요.", "/resume");
        if (type.contains("EDUCATION")) return new ActionTemplate("교육", "학력·교육 이수 계획 점검", "요구 학력 또는 교육 이수 여부를 확인하고, 부족한 경우 관련 과정 수료 계획을 세우세요.", "성장 기회 추천에서 교육 프로그램을 확인하세요.", "/opportunities");
        return new ActionTemplate("확인", "요건 확인 및 보강 계획", "공고 원문에서 이 요건이 필수인지 우대인지 먼저 확인한 뒤, 증빙 가능한 경험을 추가하세요.", "역량 프로필의 기술·자격증·프로젝트를 최신 내용으로 갱신하세요.", "/profile");
    }
    private record ActionTemplate(String category, String title, String description, String nextStep, String href) {}
    private static final class ActionGroup {
        private final ActionTemplate template;
        private final List<Long> requirementIds = new ArrayList<>();
        private final List<String> requirements = new ArrayList<>();
        private final String requirementType;
        private ActionGroup(ActionTemplate template, String requirementType) { this.template = template; this.requirementType = requirementType; }
        private void add(Long requirementId, String requirement) {
            if (requirementId != null && !requirementIds.contains(requirementId)) requirementIds.add(requirementId);
            if (!requirement.isBlank() && !requirements.contains(requirement)) requirements.add(requirement);
        }
        private GrowthActionResponse toResponse(RequirementLearningResourceService learningResources) {
            Long primaryId = requirementIds.isEmpty() ? null : requirementIds.get(0);
            String summary = String.join(" · ", requirements.stream().limit(3).toList());
            if (requirements.size() > 3) summary += " 외 " + (requirements.size() - 3) + "건";
            List<GrowthResourceRecommendationResponse> resources = learningResources.recommend(summary, requirementType, primaryId);
            if (resources.isEmpty()) return null;
            return new GrowthActionResponse(primaryId, summary, template.category(), template.title(), template.description(), template.nextStep(), template.href(), List.copyOf(requirementIds), resources);
        }
    }
    private String certificate(String r) { String v=r.toLowerCase(Locale.ROOT); if(v.contains("sql"))return "SQLD 자격증으로 데이터 역량 보강"; if(v.contains("정보처리"))return "정보처리기사 취득 계획"; if(v.contains("aws")||v.contains("cloud"))return "AWS Cloud Practitioner 학습 계획"; return "요구 분야 자격증 탐색"; }
    private static String learningKeyword(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("rag") || lower.contains("검색 증강") || lower.contains("검색증강")) return "RAG";
        if (lower.contains("spring")) return "Spring";
        if (lower.contains("java")) return "Java";
        if (lower.contains("python")) return "Python";
        if (lower.contains("react")) return "React";
        if (lower.contains("aws") || lower.contains("cloud") || lower.contains("docker") || lower.contains("컨테이너")) return "AWS Docker";
        if (lower.contains("sql") || lower.contains("데이터") || lower.contains("db")) return "SQL 데이터";
        if (lower.contains("prompt") || lower.contains("프롬프트") || lower.contains("structured output")) return "LLM 프롬프트";
        if (lower.contains("api") || lower.contains("연동")) return "API 연동";
        if (lower.contains("ai") || lower.contains("llm") || lower.contains("rag")) return "AI LLM";
        if (lower.contains("보안") || lower.contains("네트워크")) return "정보보안 네트워크";
        return "";
    }
    private String value(String v) { return v == null ? "" : v; }
}

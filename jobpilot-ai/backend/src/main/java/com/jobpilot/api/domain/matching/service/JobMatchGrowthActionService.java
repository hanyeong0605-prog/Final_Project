package com.jobpilot.api.domain.matching.service;

import com.jobpilot.api.domain.matching.dto.GrowthActionResponse;
import com.jobpilot.api.domain.matching.dto.JobMatchDetailResponse;
import com.jobpilot.api.domain.matching.dto.JobMatchEvidenceResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class JobMatchGrowthActionService {
    private final JobMatchService matches;
    public JobMatchGrowthActionService(JobMatchService matches) { this.matches = matches; }
    public List<GrowthActionResponse> forMatch(Long memberId, Long jobPostingId) {
        JobMatchDetailResponse detail = matches.findDetail(memberId, jobPostingId); List<GrowthActionResponse> result = new ArrayList<>();
        for (JobMatchEvidenceResponse evidence : detail.evidences()) {
            if ("DIRECT".equals(evidence.status())) continue;
            String requirement = value(evidence.requirement()); String type = value(evidence.requirementType()).toUpperCase(Locale.ROOT);
            if (type.contains("CERT")) result.add(new GrowthActionResponse(evidence.requirementId(), requirement, "자격증", certificate(requirement), "요구 요건과 연결되는 국가·민간 자격 종목을 찾아 취득 계획을 세워보세요.", "자격증 검색에서 종목을 확인하고 취득 예정일을 역량 프로필에 기록하세요.", "/profile"));
            else if (type.contains("SKILL")) result.add(new GrowthActionResponse(evidence.requirementId(), requirement, "실습·강의", "요구 기술 미니 프로젝트 만들기", "강의 수강만으로 끝내지 말고, 해당 기술을 사용한 결과물 1개를 포트폴리오에 남기세요.", "성장 기회 추천에서 관련 교육·부트캠프·공모전을 찾아보세요.", "/opportunities"));
            else if (type.contains("EXPERIENCE")) result.add(new GrowthActionResponse(evidence.requirementId(), requirement, "프로젝트", "경험을 증명할 프로젝트 설계", "요구 경험을 작은 과제로 쪼개어 역할·문제·해결·결과를 남기는 방식으로 보강하세요.", "이력서 작성 도우미에서 프로젝트 경험을 STAR 형식으로 정리하세요.", "/resume"));
            else if (type.contains("EDUCATION")) result.add(new GrowthActionResponse(evidence.requirementId(), requirement, "교육", "학력·교육 이수 계획 점검", "요구 학력 또는 교육 이수 여부를 확인하고, 부족한 경우 관련 과정 수료 계획을 세우세요.", "성장 기회 추천에서 교육 프로그램을 확인하세요.", "/opportunities"));
            else result.add(new GrowthActionResponse(evidence.requirementId(), requirement, "확인", "요건 확인 및 보강 계획", "공고 원문에서 이 요건이 필수인지 우대인지 먼저 확인한 뒤, 증빙 가능한 경험을 추가하세요.", "역량 프로필의 기술·자격증·프로젝트를 최신 내용으로 갱신하세요.", "/profile"));
        }
        return result;
    }
    private String certificate(String r) { String v=r.toLowerCase(Locale.ROOT); if(v.contains("sql"))return "SQLD 자격증으로 데이터 역량 보강"; if(v.contains("정보처리"))return "정보처리기사 취득 계획"; if(v.contains("aws")||v.contains("cloud"))return "AWS Cloud Practitioner 학습 계획"; return "요구 분야 자격증 탐색"; }
    private String value(String v) { return v == null ? "" : v; }
}

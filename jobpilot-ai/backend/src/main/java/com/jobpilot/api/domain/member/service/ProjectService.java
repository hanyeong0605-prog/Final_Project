package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.member.dto.ProjectRequest;
import com.jobpilot.api.domain.member.dto.ProjectResponse;
import com.jobpilot.api.domain.member.entity.Project;
import com.jobpilot.api.domain.member.repository.ProjectRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

// 2026-08-10: 이력서 작성 도우미(태스크 #58) - 프로젝트 경험(STAR 구조) CRUD.
// SelfIntroductionService와 같은 원칙: ai-server가 질문식 작성/첨삭으로 다듬어준 텍스트를
// 그대로 저장하는 역할만 한다 - 생성 자체는 여기 책임이 아니다.
@Service
@Transactional
public class ProjectService {
    private final ProjectRepository repository;
    private final ResumeCareerSyncService careerSync;

    public ProjectService(ProjectRepository repository, ResumeCareerSyncService careerSync) {
        this.repository = repository;
        this.careerSync = careerSync;
    }

    public List<ProjectResponse> list(Long memberId) {
        return repository.findByMemberId(memberId).stream().map(this::response).toList();
    }

    public ProjectResponse create(Long memberId, ProjectRequest request) {
        validate(request);
        Project saved = repository.save(new Project(memberId, request.title().trim(),
                clean(request.roleDescription()), clean(request.problemDescription()),
                clean(request.solutionDescription()), clean(request.resultDescription()),
                clean(request.githubUrl()), clean(request.deploymentUrl()),
                request.startedAt(), request.endedAt()));
        careerSync.reflectFromResume(memberId); // 태스크 #63 "반영" - 저장 성공 후 기술 요약 재합성
        return response(saved);
    }

    public ProjectResponse update(Long memberId, Long id, ProjectRequest request) {
        validate(request);
        Project project = editable(memberId, id);
        project.update(request.title().trim(), clean(request.roleDescription()), clean(request.problemDescription()),
                clean(request.solutionDescription()), clean(request.resultDescription()),
                clean(request.githubUrl()), clean(request.deploymentUrl()), request.startedAt(), request.endedAt());
        careerSync.reflectFromResume(memberId);
        return response(project);
    }

    public void delete(Long memberId, Long id) {
        repository.delete(editable(memberId, id));
    }

    private void validate(ProjectRequest request) {
        if (request.startedAt() != null && request.endedAt() != null && request.endedAt().isBefore(request.startedAt()))
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
    }

    private Project editable(Long memberId, Long id) {
        return repository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("프로젝트를 찾을 수 없습니다."));
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private ProjectResponse response(Project p) {
        return new ProjectResponse(p.getId(), p.getTitle(), p.getRoleDescription(), p.getProblemDescription(),
                p.getSolutionDescription(), p.getResultDescription(), p.getGithubUrl(), p.getDeploymentUrl(),
                p.getStartedAt(), p.getEndedAt());
    }
}

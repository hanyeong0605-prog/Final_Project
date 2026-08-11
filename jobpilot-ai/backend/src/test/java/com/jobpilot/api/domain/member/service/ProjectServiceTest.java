package com.jobpilot.api.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.api.domain.member.dto.ProjectRequest;
import com.jobpilot.api.domain.member.dto.ProjectResponse;
import com.jobpilot.api.domain.member.entity.Project;
import com.jobpilot.api.domain.member.repository.ProjectRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2026-08-10: 이력서 작성 도우미 - 프로젝트 경험(STAR) CRUD 서비스 테스트. SelfIntroduction
// 쪽과 같은 소유권 검증 외에, PlannerEventService와 같은 패턴의 "종료일 < 시작일" 검증도
// 추가로 확인한다.
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    @Mock private ProjectRepository repository;
    // 2026-08-10: 태스크 #63 반영 - SelfIntroductionServiceTest와 같은 이유로 스터빙 없이
    // mock만 주입한다.
    @Mock private ResumeCareerSyncService careerSync;

    private ProjectRequest request(LocalDate startedAt, LocalDate endedAt) {
        return new ProjectRequest("프로젝트", "역할", "문제", "해결", "결과", null, null, startedAt, endedAt);
    }

    @Test
    void createSavesProjectScopedToMember() {
        ProjectService service = new ProjectService(repository, careerSync);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse result = service.create(1L, request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)));

        assertThat(result.title()).isEqualTo("프로젝트");
        assertThat(result.roleDescription()).isEqualTo("역할");
        verify(careerSync).reflectFromResume(1L);
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        ProjectService service = new ProjectService(repository, careerSync);

        assertThatThrownBy(() ->
                service.create(1L, request(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1)))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateRejectsProjectBelongingToAnotherMember() {
        ProjectService service = new ProjectService(repository, careerSync);
        when(repository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.update(1L, 10L, request(null, null))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesOnlyWhenOwnedByRequestingMember() {
        ProjectService service = new ProjectService(repository, careerSync);
        Project owned = new Project(1L, "프로젝트", "역할", "문제", "해결", "결과", null, null, null, null);
        when(repository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.of(owned));

        service.delete(1L, 10L);

        verify(repository).delete(owned);
    }

    @Test
    void listReturnsProjectsForMember() {
        ProjectService service = new ProjectService(repository, careerSync);
        Project project = new Project(1L, "프로젝트", "역할", "문제", "해결", "결과", null, null, null, null);
        when(repository.findByMemberId(1L)).thenReturn(List.of(project));

        List<ProjectResponse> result = service.list(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("프로젝트");
    }
}

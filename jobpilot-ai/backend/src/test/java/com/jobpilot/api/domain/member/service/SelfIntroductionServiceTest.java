package com.jobpilot.api.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.api.domain.member.dto.SelfIntroductionRequest;
import com.jobpilot.api.domain.member.dto.SelfIntroductionResponse;
import com.jobpilot.api.domain.member.entity.SelfIntroduction;
import com.jobpilot.api.domain.member.repository.SelfIntroductionRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2026-08-10: 이력서 작성 도우미 - 자기소개서 CRUD 서비스 테스트. repository는 Mockito로
// 대체하고, "대표 자기소개서는 회원당 하나만 유지된다"는 핵심 규칙과 "본인 소유가 아닌
// 글은 수정/삭제 못 한다"는 소유권 검증을 중점적으로 확인한다.
@ExtendWith(MockitoExtension.class)
class SelfIntroductionServiceTest {
    @Mock private SelfIntroductionRepository repository;
    // 2026-08-10: 태스크 #63 반영 - create/update 성공 후 careerSync.reflectFromResume()을
    // 부르지만, 여기선 그 내부 동작(ai-server 호출)까지 검증하지 않는다(별도
    // ResumeCareerSyncServiceTest 담당). 스터빙 없이 mock으로만 둬도 void 메서드라 strict
    // stub 모드에서 문제되지 않는다.
    @Mock private ResumeCareerSyncService careerSync;

    @Test
    void createSavesEntryScopedToMember() {
        // primary=false라 clearOtherPrimaries()가 호출되지 않는다 - MockitoExtension이
        // 기본으로 strict stub 모드라, 실제로 안 쓰이는 findByMemberIdOrderByUpdatedAtDesc
        // 스터빙을 여기 넣으면 UnnecessaryStubbingException이 난다.
        SelfIntroductionService service = new SelfIntroductionService(repository, careerSync);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SelfIntroductionResponse result = service.create(1L, new SelfIntroductionRequest("제목", "내용", false));

        assertThat(result.title()).isEqualTo("제목");
        assertThat(result.content()).isEqualTo("내용");
        assertThat(result.primary()).isFalse();
        verify(careerSync).reflectFromResume(1L);
    }

    @Test
    void creatingNewPrimaryUnsetsExistingPrimaryEntries() {
        SelfIntroductionService service = new SelfIntroductionService(repository, careerSync);
        SelfIntroduction existingPrimary = new SelfIntroduction(1L, "기존 대표글", "내용", true);
        when(repository.findByMemberIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(existingPrimary));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(1L, new SelfIntroductionRequest("새 대표글", "내용", true));

        assertThat(existingPrimary.isPrimary()).isFalse();
    }

    @Test
    void updateRejectsEntryBelongingToAnotherMember() {
        SelfIntroductionService service = new SelfIntroductionService(repository, careerSync);
        when(repository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.update(1L, 10L, new SelfIntroductionRequest("제목", "내용", false))
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesOnlyWhenOwnedByRequestingMember() {
        SelfIntroductionService service = new SelfIntroductionService(repository, careerSync);
        SelfIntroduction owned = new SelfIntroduction(1L, "제목", "내용", false);
        when(repository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.of(owned));

        service.delete(1L, 10L);

        verify(repository).delete(owned);
    }

    @Test
    void listReturnsEntriesOrderedByRepository() {
        SelfIntroductionService service = new SelfIntroductionService(repository, careerSync);
        SelfIntroduction entry = new SelfIntroduction(1L, "제목", "내용", true);
        when(repository.findByMemberIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(entry));

        List<SelfIntroductionResponse> result = service.list(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("제목");
    }
}

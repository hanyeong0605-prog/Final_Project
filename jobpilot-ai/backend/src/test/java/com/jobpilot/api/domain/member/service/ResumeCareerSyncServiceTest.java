package com.jobpilot.api.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.api.domain.member.client.ResumeSummaryAiClient;
import com.jobpilot.api.domain.member.entity.MemberProfile;
import com.jobpilot.api.domain.member.entity.MemberSpecification;
import com.jobpilot.api.domain.member.entity.Project;
import com.jobpilot.api.domain.member.entity.SelfIntroduction;
import com.jobpilot.api.domain.member.repository.MemberProfileRepository;
import com.jobpilot.api.domain.member.repository.MemberSpecificationRepository;
import com.jobpilot.api.domain.member.repository.ProjectRepository;
import com.jobpilot.api.domain.member.repository.SelfIntroductionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2026-08-10: 태스크 #63 "반영" 방향 - 자기소개서/프로젝트 저장 후 기술 요약을 다시
// 합성해서 MemberSpecification에 반영하는 로직. 핵심은 "ai-server 호출이 실패해도 예외가
// 밖으로 새면 안 된다"는 fail-open 보장과, "내용이 하나도 없으면 아예 호출하지 않는다"는
// 가드다.
@ExtendWith(MockitoExtension.class)
class ResumeCareerSyncServiceTest {
    @Mock private MemberProfileRepository profiles;
    @Mock private MemberSpecificationRepository specifications;
    @Mock private SelfIntroductionRepository selfIntroductions;
    @Mock private ProjectRepository projects;
    @Mock private ResumeSummaryAiClient aiClient;

    private ResumeCareerSyncService service() {
        return new ResumeCareerSyncService(profiles, specifications, selfIntroductions, projects, aiClient);
    }

    @Test
    void reflectsSynthesizedSummaryIntoSpecification() {
        MemberProfile profile = new MemberProfile(1L);
        profile.update("백엔드 개발자", "개발", null, null, "ENTRY", null);
        when(profiles.findById(1L)).thenReturn(Optional.of(profile));
        MemberSpecification spec = new MemberSpecification(1L);
        when(specifications.findById(1L)).thenReturn(Optional.of(spec));
        when(selfIntroductions.findByMemberIdOrderByUpdatedAtDesc(1L))
                .thenReturn(List.of(new SelfIntroduction(1L, "제목", "자기소개서 본문", false)));
        when(projects.findByMemberId(1L)).thenReturn(List.of());
        when(aiClient.synthesizeTechnicalSummary(anyString(), any(), anyList(), anyList()))
                .thenReturn(Map.of("ok", true, "summary", "새로 합성된 기술 요약"));

        service().reflectFromResume(1L);

        assertThat(spec.getTechnicalSummary()).isEqualTo("새로 합성된 기술 요약");
        verify(specifications).save(spec);
    }

    @Test
    void skipsAiCallWhenNoResumeContentExists() {
        // 자기소개서도 프로젝트도 없으면(아직 아무것도 안 쓴 회원) ai-server를 부를 이유가
        // 없다 - 매 저장(예: 첫 자기소개서 작성 이전 다른 흐름)마다 불필요한 호출을 막는다.
        when(profiles.findById(1L)).thenReturn(Optional.empty());
        when(specifications.findById(1L)).thenReturn(Optional.empty());
        when(selfIntroductions.findByMemberIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(projects.findByMemberId(1L)).thenReturn(List.of());

        service().reflectFromResume(1L);

        verify(aiClient, never()).synthesizeTechnicalSummary(any(), any(), any(), any());
        verify(specifications, never()).save(any());
    }

    @Test
    void doesNotThrowWhenAiClientFails() {
        // ai-server가 죽어있어도(RestClientException 등) 이력서 저장 흐름 자체가 깨지면 안
        // 된다 - 이 서비스는 create/update 트랜잭션 안에서 호출되므로 예외가 새면 저장까지
        // 롤백된다.
        when(profiles.findById(1L)).thenReturn(Optional.empty());
        when(specifications.findById(1L)).thenReturn(Optional.empty());
        when(selfIntroductions.findByMemberIdOrderByUpdatedAtDesc(1L))
                .thenReturn(List.of(new SelfIntroduction(1L, "제목", "본문", false)));
        when(projects.findByMemberId(1L)).thenReturn(List.of());
        when(aiClient.synthesizeTechnicalSummary(anyString(), any(), anyList(), anyList()))
                .thenThrow(new RuntimeException("연결 실패"));

        service().reflectFromResume(1L);

        verify(specifications, never()).save(any());
    }

    @Test
    void doesNotOverwriteSummaryWhenAiReturnsNotOk() {
        when(profiles.findById(1L)).thenReturn(Optional.empty());
        MemberSpecification spec = new MemberSpecification(1L);
        when(specifications.findById(1L)).thenReturn(Optional.of(spec));
        when(selfIntroductions.findByMemberIdOrderByUpdatedAtDesc(1L))
                .thenReturn(List.of(new SelfIntroduction(1L, "제목", "본문", false)));
        when(projects.findByMemberId(1L)).thenReturn(List.of());
        when(aiClient.synthesizeTechnicalSummary(anyString(), any(), anyList(), anyList()))
                .thenReturn(Map.of("ok", false, "message", "GEMINI_API_KEY 설정이 필요합니다."));

        service().reflectFromResume(1L);

        assertThat(spec.getTechnicalSummary()).isNull();
        verify(specifications, never()).save(any());
    }

    @Test
    void includesProjectFieldsWhenSynthesizing() {
        when(profiles.findById(1L)).thenReturn(Optional.empty());
        when(specifications.findById(1L)).thenReturn(Optional.empty());
        when(selfIntroductions.findByMemberIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(projects.findByMemberId(1L)).thenReturn(
                List.of(new Project(1L, "커머스 플랫폼", "백엔드", "동시성 이슈", "락 적용", "지연 개선", null, null, null, null)));
        when(aiClient.synthesizeTechnicalSummary(anyString(), any(), anyList(), anyList()))
                .thenReturn(Map.of("ok", true, "summary", "요약"));

        service().reflectFromResume(1L);

        verify(aiClient).synthesizeTechnicalSummary(anyString(), any(), anyList(), anyList());
    }
}

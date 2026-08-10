package com.jobpilot.api.domain.member.service;

import com.jobpilot.api.domain.member.dto.SelfIntroductionRequest;
import com.jobpilot.api.domain.member.dto.SelfIntroductionResponse;
import com.jobpilot.api.domain.member.entity.SelfIntroduction;
import com.jobpilot.api.domain.member.repository.SelfIntroductionRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

// 2026-08-10: 이력서 작성 도우미 기능(태스크 - 이력서 먼저 끝내고 모의면접 질문 생성이랑
// 연동) 중 자기소개서 CRUD 부분. 엔티티/레포지토리는 이미 있었는데 Service/Controller가
// 없어서 아무 화면에서도 안 쓰이고 있었다. ai-server의 질문식 작성/첨삭(별도 태스크)이
// 다듬어준 텍스트를 그대로 여기 저장하는 구조 - AI 생성 자체는 이 서비스의 책임이 아니다
// (기존 모의면접 기능과 같은 원칙: ai-server는 생성만, Spring은 영속성만).
@Service
@Transactional
public class SelfIntroductionService {
    private final SelfIntroductionRepository repository;
    private final ResumeCareerSyncService careerSync;

    public SelfIntroductionService(SelfIntroductionRepository repository, ResumeCareerSyncService careerSync) {
        this.repository = repository;
        this.careerSync = careerSync;
    }

    public List<SelfIntroductionResponse> list(Long memberId) {
        return repository.findByMemberIdOrderByUpdatedAtDesc(memberId).stream().map(this::response).toList();
    }

    public SelfIntroductionResponse create(Long memberId, SelfIntroductionRequest request) {
        if (request.primary()) clearOtherPrimaries(memberId, null);
        SelfIntroduction saved = repository.save(
                new SelfIntroduction(memberId, request.title().trim(), request.content().trim(), request.primary()));
        careerSync.reflectFromResume(memberId); // 태스크 #63 "반영" - 저장 성공 후 기술 요약 재합성
        return response(saved);
    }

    public SelfIntroductionResponse update(Long memberId, Long id, SelfIntroductionRequest request) {
        SelfIntroduction entry = editable(memberId, id);
        if (request.primary()) clearOtherPrimaries(memberId, id);
        entry.update(request.title().trim(), request.content().trim(), request.primary());
        careerSync.reflectFromResume(memberId);
        return response(entry);
    }

    public void delete(Long memberId, Long id) {
        repository.delete(editable(memberId, id));
    }

    // 대표 자기소개서는 회원당 하나만 유지한다 - 새로 지정하는 대상(excludeId, 신규 생성이면
    // null)을 제외한 나머지의 primary 플래그를 전부 끈다.
    private void clearOtherPrimaries(Long memberId, Long excludeId) {
        for (SelfIntroduction other : repository.findByMemberIdOrderByUpdatedAtDesc(memberId)) {
            if (other.isPrimary() && !other.getId().equals(excludeId)) other.unsetPrimary();
        }
    }

    private SelfIntroduction editable(Long memberId, Long id) {
        return repository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("자기소개서를 찾을 수 없습니다."));
    }

    private SelfIntroductionResponse response(SelfIntroduction entry) {
        return new SelfIntroductionResponse(entry.getId(), entry.getTitle(), entry.getContent(), entry.isPrimary(),
                entry.getCreatedAt(), entry.getUpdatedAt());
    }
}

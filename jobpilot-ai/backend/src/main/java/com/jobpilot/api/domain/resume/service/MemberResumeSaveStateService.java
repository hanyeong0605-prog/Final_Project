package com.jobpilot.api.domain.resume.service;

import com.jobpilot.api.domain.resume.dto.ResumeSaveStateResponse;
import com.jobpilot.api.domain.resume.entity.MemberResumeSaveState;
import com.jobpilot.api.domain.resume.repository.MemberResumeSaveStateRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class MemberResumeSaveStateService {
    private final MemberResumeSaveStateRepository states;
    public MemberResumeSaveStateService(MemberResumeSaveStateRepository states) { this.states = states; }
    public ResumeSaveStateResponse get(Long memberId) { return states.findById(memberId).map(value -> new ResumeSaveStateResponse(value.getSaveStatus(), value.getUpdatedAt())).orElse(new ResumeSaveStateResponse("NOT_SAVED", null)); }
    public ResumeSaveStateResponse save(Long memberId, String status) {
        MemberResumeSaveState state = states.findById(memberId).orElseGet(() -> new MemberResumeSaveState(memberId, status));
        if (state.getUpdatedAt() != null) state.update(status);
        MemberResumeSaveState saved = states.save(state);
        return new ResumeSaveStateResponse(saved.getSaveStatus(), saved.getUpdatedAt());
    }
}

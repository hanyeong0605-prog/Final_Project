package com.jobpilot.api.domain.resume.service;

import com.jobpilot.api.domain.resume.dto.ResumeEntryRequest;
import com.jobpilot.api.domain.resume.dto.ResumeEntryResponse;
import com.jobpilot.api.domain.resume.entity.ResumeEntry;
import com.jobpilot.api.domain.resume.entity.ResumeEntryType;
import com.jobpilot.api.domain.resume.repository.ResumeEntryRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional
public class ResumeEntryService {
    private final ResumeEntryRepository entries;
    public ResumeEntryService(ResumeEntryRepository entries) { this.entries = entries; }
    public List<ResumeEntryResponse> list(Long memberId) { return entries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId).stream().map(ResumeEntryResponse::from).toList(); }
    public ResumeEntryResponse create(Long memberId, ResumeEntryRequest request) {
        if (request.entryType() == ResumeEntryType.PERSONAL
                && entries.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(memberId).stream().anyMatch(entry -> entry.getEntryType() == ResumeEntryType.PERSONAL)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "인적사항은 회원당 한 건만 등록할 수 있습니다.");
        }
        return ResumeEntryResponse.from(entries.save(new ResumeEntry(memberId, request.entryType(), request.title().trim(), request.content(), request.displayOrder())));
    }
    public ResumeEntryResponse update(Long memberId, Long id, ResumeEntryRequest request) { ResumeEntry entry = owned(memberId, id); if (entry.getEntryType() != request.entryType()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이력 항목 유형은 변경할 수 없습니다."); entry.update(request.title().trim(), request.content(), request.displayOrder()); return ResumeEntryResponse.from(entry); }
    public void delete(Long memberId, Long id) { if (entries.deleteByIdAndMemberId(id, memberId) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이력 항목을 찾을 수 없습니다."); }
    private ResumeEntry owned(Long memberId, Long id) { return entries.findByIdAndMemberId(id, memberId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이력 항목을 찾을 수 없습니다.")); }
}

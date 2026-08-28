package com.jobpilot.api.domain.resume.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.resume.dto.ResumeEntryRequest;
import com.jobpilot.api.domain.resume.entity.ResumeEntry;
import com.jobpilot.api.domain.resume.entity.ResumeEntryType;
import com.jobpilot.api.domain.resume.repository.ResumeEntryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ResumeEntryServiceTest {
    @Mock private ResumeEntryRepository repository;

    @Test
    void createRejectsSecondPersonalEntryForSameMember() {
        ResumeEntryService service = new ResumeEntryService(repository);
        ObjectMapper objectMapper = new ObjectMapper();
        ResumeEntry existing = new ResumeEntry(1L, ResumeEntryType.PERSONAL, "인적사항", objectMapper.createObjectNode(), 0);
        ResumeEntryRequest request = new ResumeEntryRequest(ResumeEntryType.PERSONAL, "인적사항", objectMapper.createObjectNode(), 0);
        when(repository.findByMemberIdOrderByEntryTypeAscDisplayOrderAscIdAsc(1L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}

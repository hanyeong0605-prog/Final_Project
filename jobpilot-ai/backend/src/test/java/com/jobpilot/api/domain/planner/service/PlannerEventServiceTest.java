package com.jobpilot.api.domain.planner.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobpilot.api.domain.interest.service.InterestService;
import com.jobpilot.api.domain.member.service.CertificateBookmarkService;
import com.jobpilot.api.domain.planner.entity.PlannerEvent;
import com.jobpilot.api.domain.planner.repository.PlannerEventRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlannerEventServiceTest {
    @Test
    void deletingRecruitmentScheduleAlsoRemovesTheJobBookmark() {
        PlannerEventRepository repository = mock(PlannerEventRepository.class);
        InterestService interests = mock(InterestService.class);
        PlannerEvent event = PlannerEvent.fromJobPosting(1L, 55L, "공고", LocalDateTime.now(), LocalDateTime.now());
        when(repository.findByIdAndMemberId(10L, 1L)).thenReturn(Optional.of(event));

        new PlannerEventService(repository, mock(CertificateBookmarkService.class), interests).delete(1L, 10L);

        verify(interests).removeJobBookmark(1L, 55L);
    }

    @Test
    void deletingCertificateScheduleAlsoRemovesItsBookmarkAndAllRounds() {
        PlannerEventRepository repository = mock(PlannerEventRepository.class);
        CertificateBookmarkService certificates = mock(CertificateBookmarkService.class);
        PlannerEvent event = PlannerEvent.fromCertificate(1L, 14L, "자격증", "CERTIFICATE_WRITTEN",
                LocalDateTime.now(), LocalDateTime.now());
        when(repository.findByIdAndMemberId(11L, 1L)).thenReturn(Optional.of(event));

        new PlannerEventService(repository, certificates, mock(InterestService.class)).delete(1L, 11L);

        verify(certificates).removeById(1L, 14L);
    }
}

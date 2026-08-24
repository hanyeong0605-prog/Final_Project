package com.jobpilot.api.domain.interview.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CameraPairingServiceTest {
    private final CameraPairingService service = new CameraPairingService(
            Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void sameOwnerCanJoinAgainWithTheSameUnexpiredQr() {
        CameraPairingService.CreatedPairing created = service.create(17L);

        CameraPairingService.JoinedPairing first = service.join(17L, created.roomId(), created.pairingToken());
        CameraPairingService.JoinedPairing retried = service.join(17L, created.roomId(), created.pairingToken());

        assertThat(retried.roomId()).isEqualTo(first.roomId());
        assertThat(retried.socketTicket()).isNotEqualTo(first.socketTicket());
    }

    @Test
    void anotherAccountCannotUseTheQr() {
        CameraPairingService.CreatedPairing created = service.create(17L);

        assertThatThrownBy(() -> service.join(18L, created.roomId(), created.pairingToken()))
                .isInstanceOf(PairingException.class)
                .hasMessageContaining("만든 계정");
    }
}

package com.jobpilot.api.domain.admin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdminFacePairingServiceTest {

    @Test
    void rejectedCaptureCanBeRetriedWithTheSameUnexpiredQrSession() {
        AdminFacePairingService service = new AdminFacePairingService();
        AdminFacePairingService.Created created = service.create(42L);

        service.verifyOwner(42L, created.sessionId(), created.token());
        service.complete(42L, created.sessionId(), false, 61.2, "일치율 미달");
        assertEquals(AdminFacePairingService.Status.REJECTED, service.result(42L, created.sessionId()).status());

        assertDoesNotThrow(() -> service.verifyOwner(42L, created.sessionId(), created.token()));
        service.complete(42L, created.sessionId(), true, 91.4, "인증 성공");

        assertEquals(AdminFacePairingService.Status.VERIFIED, service.result(42L, created.sessionId()).status());
        assertTrue(service.isVerified(42L, created.sessionId()));
    }
}

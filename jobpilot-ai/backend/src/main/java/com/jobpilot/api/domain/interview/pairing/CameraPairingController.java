package com.jobpilot.api.domain.interview.pairing;

import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/camera-pairings")
public class CameraPairingController {
    private final CameraPairingService service;

    public CameraPairingController(CameraPairingService service) { this.service = service; }

    @PostMapping
    public CreatePairingResponse create(Authentication authentication) {
        CameraPairingService.CreatedPairing pairing = service.create(AuthenticatedMember.id(authentication));
        return new CreatePairingResponse(pairing.roomId(), pairing.pairingToken(), pairing.socketTicket(), pairing.expiresAt());
    }

    @PostMapping("/join")
    public JoinPairingResponse join(Authentication authentication, @Valid @RequestBody JoinPairingRequest request) {
        CameraPairingService.JoinedPairing pairing = service.join(AuthenticatedMember.id(authentication), request.roomId(), request.pairingToken());
        return new JoinPairingResponse(pairing.roomId(), pairing.socketTicket(), pairing.expiresAt());
    }

    public record CreatePairingResponse(String roomId, String pairingToken, String socketTicket, Instant expiresAt) {}
    public record JoinPairingResponse(String roomId, String socketTicket, Instant expiresAt) {}
    public record JoinPairingRequest(@NotBlank String roomId, @NotBlank String pairingToken) {}
}

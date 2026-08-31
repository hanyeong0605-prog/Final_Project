package com.jobpilot.api.domain.assistant.controller;

import com.jobpilot.api.domain.assistant.service.AssistantAiClient;
import com.jobpilot.api.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/assistant")
public class AssistantController {
    private final AssistantAiClient client;

    public AssistantController(AssistantAiClient client) { this.client = client; }

    @PostMapping("/chat")
    public Map<String, Object> chat(Authentication authentication, @Valid @RequestBody Request request) {
        var history = request.history() == null ? List.<Turn>of() : request.history().stream().limit(10).toList();
        return client.chat(AuthenticatedMember.id(authentication), request.message(), history.stream()
                .map(turn -> Map.of("role", turn.role(), "content", turn.content()))
                .toList());
    }

    public record Request(@NotBlank @Size(max = 3000) String message, @Size(max = 10) List<@Valid Turn> history) {}
    public record Turn(@NotBlank @Size(max = 20) String role, @NotBlank @Size(max = 3000) String content) {}
}

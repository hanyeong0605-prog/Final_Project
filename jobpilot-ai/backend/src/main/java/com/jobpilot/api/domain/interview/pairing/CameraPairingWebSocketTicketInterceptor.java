package com.jobpilot.api.domain.interview.pairing;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CameraPairingWebSocketTicketInterceptor implements HandshakeInterceptor {
    static final String IDENTITY_ATTRIBUTE = "cameraPairingIdentity";
    private final CameraPairingService service;

    public CameraPairingWebSocketTicketInterceptor(CameraPairingService service) { this.service = service; }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String ticket = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
        if (ticket == null || ticket.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            attributes.put(IDENTITY_ATTRIBUTE, service.consumeSocketTicket(ticket));
            return true;
        } catch (PairingException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {}
}

package com.jobpilot.api.domain.interview.pairing;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class CameraPairingWebSocketConfig implements WebSocketConfigurer {
    private final CameraPairingWebSocketHandler handler;
    private final CameraPairingWebSocketTicketInterceptor ticketInterceptor;
    private final String[] allowedOrigins;

    public CameraPairingWebSocketConfig(
            CameraPairingWebSocketHandler handler,
            CameraPairingWebSocketTicketInterceptor ticketInterceptor,
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins
    ) {
        this.handler = handler;
        this.ticketInterceptor = ticketInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/camera-pair")
                .addInterceptors(ticketInterceptor)
                .setAllowedOriginPatterns(allowedOrigins);
    }
}

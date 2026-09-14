package com.tunetogether.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * STOMP-over-WebSocket wiring. The broker is split by concern into {@code /topic}
 * destinations (state/queue/chat, each its own topic per room so a chatty chat stream
 * can never interfere with time-critical playback sync) and {@code /app}-prefixed
 * application destinations handled by {@link RoomStompController}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtStompChannelInterceptor jwtStompChannelInterceptor;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(JwtStompChannelInterceptor jwtStompChannelInterceptor,
                            @Value("${app.cors.allowed-origins}") String allowedOriginsProperty) {
        this.jwtStompChannelInterceptor = jwtStompChannelInterceptor;
        this.allowedOriginPatterns = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .toArray(String[]::new);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtStompChannelInterceptor);
        // NOTE: Spring's TaskExecutorRegistration#taskExecutor(...) (reached via
        // ChannelRegistration#taskExecutor(...)) only accepts a concrete
        // ThreadPoolTaskExecutor, not an arbitrary java.util.concurrent.Executor, so a
        // shared virtual-thread executor can't be dropped in here without either
        // fighting that API or hand-rolling a ThreadPoolTaskExecutor subclass. Per the
        // task brief this specific optimization is optional -- correctness comes
        // entirely from the per-room SerialExecutor actor model (see room.PartyRoom),
        // not from which pool runs the STOMP inbound channel -- so we intentionally
        // leave Spring's default channel executor in place here.
    }
}

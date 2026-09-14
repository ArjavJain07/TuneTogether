package com.tunetogether.ws;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * O(1) lookup from a live STOMP session id to the (room, participant) it is bound to,
 * so a disconnect can be routed straight to the right room without scanning every
 * registered room. Populated by {@link JwtStompChannelInterceptor} on CONNECT and
 * cleaned up by {@link RoomWebSocketEventListener} on disconnect.
 */
@Component
public class SessionRegistry {

    public record SessionInfo(String roomCode, String participantId) {}

    private final ConcurrentHashMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public void put(String sessionId, SessionInfo info) {
        sessions.put(sessionId, info);
    }

    public SessionInfo get(String sessionId) {
        return sessions.get(sessionId);
    }

    public SessionInfo remove(String sessionId) {
        return sessions.remove(sessionId);
    }
}

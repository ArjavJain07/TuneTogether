package com.tunetogether.ws;

import com.tunetogether.auth.InvalidTokenException;
import com.tunetogether.auth.JwtService;
import com.tunetogether.auth.TuneTogetherPrincipal;
import com.tunetogether.room.PartyRoom;
import com.tunetogether.room.RoomRegistry;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Binds room membership to the WebSocket connection itself at STOMP CONNECT time --
 * there is no separate "register" message. Validates the JWT from the
 * {@code Authorization} header, verifies the {@code X-Room-Code}/{@code X-Participant-Id}
 * headers name a real room and an existing member of it, and on success records the
 * session in {@link SessionRegistry} and binds the live session id onto the
 * participant record.
 *
 * <p>Registered on the inbound channel by {@code WebSocketConfig}. DISCONNECT handling
 * lives in {@link RoomWebSocketEventListener} instead (a {@code SessionDisconnectEvent}
 * listener), which fires reliably for both clean and abrupt/transport-level disconnects.
 */
@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final RoomRegistry roomRegistry;
    private final SessionRegistry sessionRegistry;

    public JwtStompChannelInterceptor(JwtService jwtService, RoomRegistry roomRegistry,
                                       SessionRegistry sessionRegistry) {
        this.jwtService = jwtService;
        this.roomRegistry = roomRegistry;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
        }
        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String token = stripBearerPrefix(accessor.getFirstNativeHeader("Authorization"));
        if (token == null) {
            throw new MessageDeliveryException("Missing Authorization header on STOMP CONNECT");
        }

        TuneTogetherPrincipal principal;
        try {
            principal = jwtService.parseAndValidate(token);
        } catch (InvalidTokenException e) {
            throw new MessageDeliveryException("Invalid JWT on STOMP CONNECT: " + e.getMessage());
        }

        String roomCode = accessor.getFirstNativeHeader("X-Room-Code");
        String participantId = accessor.getFirstNativeHeader("X-Participant-Id");
        if (roomCode == null || participantId == null) {
            throw new MessageDeliveryException("Missing X-Room-Code/X-Participant-Id headers on STOMP CONNECT");
        }

        PartyRoom room = roomRegistry.find(roomCode)
                .orElseThrow(() -> new MessageDeliveryException("Room " + roomCode + " does not exist"));

        if (!room.hasParticipant(participantId)) {
            throw new MessageDeliveryException(
                    "Participant " + participantId + " is not a member of room " + roomCode);
        }

        accessor.setUser(principal);
        String sessionId = accessor.getSessionId();
        sessionRegistry.put(sessionId, new SessionRegistry.SessionInfo(roomCode, participantId));
        // Bookkeeping only, fire-and-forget: the membership check above (a direct,
        // lock-free map read) is what actually gates the handshake. Binding the live
        // session id onto the participant record is what lets a later disconnect be
        // fenced against a since-superseded session (see PartyRoom#leave).
        room.connectSession(participantId, sessionId);
    }

    private static String stripBearerPrefix(String header) {
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            trimmed = trimmed.substring(7).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}

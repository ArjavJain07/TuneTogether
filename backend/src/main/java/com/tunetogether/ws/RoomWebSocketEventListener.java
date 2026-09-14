package com.tunetogether.ws;

import com.tunetogether.room.PartyRoom;
import com.tunetogether.room.RoomRegistry;
import com.tunetogether.ws.dto.RoomDtoMapper;
import com.tunetogether.ws.dto.RoomStateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.Executor;

/**
 * Handles WebSocket disconnects: removes the departing participant from its room
 * (session-fenced, see {@link PartyRoom#leave}), broadcasts the resulting state if the
 * participant was actually removed, and eagerly reaps the room in case that leave
 * emptied it. Chosen over handling {@code StompCommand.DISCONNECT} in
 * {@link JwtStompChannelInterceptor} because {@link SessionDisconnectEvent} fires
 * reliably for both clean and abrupt/transport-level disconnects.
 */
@Component
public class RoomWebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketEventListener.class);

    private final RoomRegistry roomRegistry;
    private final SessionRegistry sessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final Executor fanOutExecutor;

    public RoomWebSocketEventListener(RoomRegistry roomRegistry, SessionRegistry sessionRegistry,
                                       SimpMessagingTemplate messagingTemplate,
                                       @Qualifier("roomFanOutExecutor") Executor fanOutExecutor) {
        this.roomRegistry = roomRegistry;
        this.sessionRegistry = sessionRegistry;
        this.messagingTemplate = messagingTemplate;
        this.fanOutExecutor = fanOutExecutor;
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        SessionRegistry.SessionInfo info = sessionRegistry.remove(sessionId);
        if (info == null) {
            return;
        }
        roomRegistry.find(info.roomCode()).ifPresent(room -> handleDisconnect(room, info, sessionId));
    }

    private void handleDisconnect(PartyRoom room, SessionRegistry.SessionInfo info, String sessionId) {
        room.leave(info.participantId(), sessionId)
                .thenCompose(leaveResult -> {
                    if (leaveResult.participantRemoved()) {
                        RoomStateMessage stateMessage = RoomDtoMapper.toStateMessage(leaveResult.snapshot());
                        fanOutExecutor.execute(() -> messagingTemplate.convertAndSend(
                                "/topic/room/" + info.roomCode() + "/state", stateMessage));
                    }
                    return roomRegistry.reapIfIdle(room);
                })
                .exceptionally(ex -> {
                    log.warn("Error handling disconnect for room {}: {}", info.roomCode(), ex.toString());
                    return null;
                });
    }
}

package com.tunetogether.ws;

import com.tunetogether.room.PartyRoom;
import com.tunetogether.room.RoomRegistry;
import com.tunetogether.ws.dto.ChatRequest;
import com.tunetogether.ws.dto.PlaybackCommandRequest;
import com.tunetogether.ws.dto.QueueAddRequest;
import com.tunetogether.ws.dto.RoomDtoMapper;
import com.tunetogether.ws.dto.TickRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

/**
 * STOMP {@code @MessageMapping} destinations. Every handler resolves {@code roomCode}/
 * {@code participantId} from {@link SessionRegistry} keyed by the STOMP session id --
 * never from a client-supplied field in the payload -- so "who is asking" and "are
 * they the host" are always answered server-side, from state the CONNECT interceptor
 * already verified.
 */
@Controller
public class RoomStompController {

    private static final Logger log = LoggerFactory.getLogger(RoomStompController.class);

    private final RoomRegistry roomRegistry;
    private final SessionRegistry sessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final Executor fanOutExecutor;

    public RoomStompController(RoomRegistry roomRegistry, SessionRegistry sessionRegistry,
                                SimpMessagingTemplate messagingTemplate,
                                @Qualifier("roomFanOutExecutor") Executor fanOutExecutor) {
        this.roomRegistry = roomRegistry;
        this.sessionRegistry = sessionRegistry;
        this.messagingTemplate = messagingTemplate;
        this.fanOutExecutor = fanOutExecutor;
    }

    @MessageMapping("/room/{code}/leave")
    public void leave(@DestinationVariable String code, SimpMessageHeaderAccessor headerAccessor) {
        withRoom(code, headerAccessor, (room, session) ->
                room.leave(session.participantId(), headerAccessor.getSessionId())
                        .thenCompose(result -> {
                            if (result.participantRemoved()) {
                                broadcastState(code, result.snapshot());
                            }
                            return roomRegistry.reapIfIdle(room);
                        })
                        .<Void>thenApply(removed -> null)
                        .exceptionally(ex -> {
                            log.warn("Error handling explicit leave for room {}: {}", code, ex.toString());
                            return null;
                        }));
    }

    @MessageMapping("/room/{code}/playback")
    public void playback(@DestinationVariable String code, @Payload PlaybackCommandRequest request,
                          SimpMessageHeaderAccessor headerAccessor) {
        withRoom(code, headerAccessor, (room, session) ->
                room.setPlayback(session.participantId(), request.action(), request.track(),
                                request.positionSeconds())
                        .thenAccept(snapshot -> broadcastState(code, snapshot))
                        .exceptionally(ex -> {
                            log.debug("Rejected playback command in room {}: {}", code, ex.toString());
                            return null;
                        }));
    }

    @MessageMapping("/room/{code}/playback/tick")
    public void tick(@DestinationVariable String code, @Payload TickRequest request,
                      SimpMessageHeaderAccessor headerAccessor) {
        withRoom(code, headerAccessor, (room, session) ->
                room.tick(session.participantId(), request.positionSeconds())
                        .thenAccept(snapshot -> broadcastState(code, snapshot))
                        .exceptionally(ex -> {
                            log.debug("Rejected tick in room {}: {}", code, ex.toString());
                            return null;
                        }));
    }

    @MessageMapping("/room/{code}/queue/add")
    public void queueAdd(@DestinationVariable String code, @Payload QueueAddRequest request,
                          SimpMessageHeaderAccessor headerAccessor) {
        withRoom(code, headerAccessor, (room, session) ->
                room.addToQueue(request.track())
                        .thenAccept(snapshot -> broadcastQueue(code, snapshot))
                        .exceptionally(ex -> {
                            log.warn("Error adding to queue in room {}: {}", code, ex.toString());
                            return null;
                        }));
    }

    @MessageMapping("/room/{code}/queue/next")
    public void queueNext(@DestinationVariable String code, SimpMessageHeaderAccessor headerAccessor) {
        withRoom(code, headerAccessor, (room, session) ->
                room.popQueue(session.participantId())
                        .thenAccept(snapshot -> {
                            broadcastQueue(code, snapshot);
                            broadcastState(code, snapshot);
                        })
                        .exceptionally(ex -> {
                            log.debug("Rejected queue/next in room {}: {}", code, ex.toString());
                            return null;
                        }));
    }

    @MessageMapping("/room/{code}/chat")
    public void chat(@DestinationVariable String code, @Payload ChatRequest request,
                      SimpMessageHeaderAccessor headerAccessor) {
        withRoom(code, headerAccessor, (room, session) -> {
            String senderName = room.participantDisplayName(session.participantId()).orElse("Unknown");
            return room.postChat(session.participantId(), senderName, request.text(), request.isEmoji())
                    .thenAccept(message -> {
                        var dto = RoomDtoMapper.toChatMessageDto(message);
                        fanOutExecutor.execute(() ->
                                messagingTemplate.convertAndSend("/topic/room/" + code + "/chat", dto));
                    })
                    .exceptionally(ex -> {
                        log.warn("Error posting chat in room {}: {}", code, ex.toString());
                        return null;
                    });
        });
    }

    /**
     * Resolves the session bound to this STOMP session id, confirms the destination's
     * room code actually matches the room that session was authorized for on CONNECT
     * (defense in depth -- destinations are otherwise routed straight off the verified
     * session), and if all that checks out, hands the room + session to {@code action}.
     * Fire-and-forget: {@code action} is expected to return a future whose completion
     * (success or failure) it has already handled internally.
     */
    private void withRoom(String code, SimpMessageHeaderAccessor headerAccessor,
                           BiFunction<PartyRoom, SessionRegistry.SessionInfo, CompletableFuture<Void>> action) {
        Optional<SessionRegistry.SessionInfo> sessionOpt =
                Optional.ofNullable(sessionRegistry.get(headerAccessor.getSessionId()));
        if (sessionOpt.isEmpty()) {
            return;
        }
        SessionRegistry.SessionInfo session = sessionOpt.get();
        if (!session.roomCode().equals(code)) {
            return;
        }
        roomRegistry.find(session.roomCode()).ifPresent(room -> action.apply(room, session));
    }

    private void broadcastState(String code, PartyRoom.RoomSnapshot snapshot) {
        var stateMessage = RoomDtoMapper.toStateMessage(snapshot);
        fanOutExecutor.execute(() -> messagingTemplate.convertAndSend("/topic/room/" + code + "/state", stateMessage));
    }

    private void broadcastQueue(String code, PartyRoom.RoomSnapshot snapshot) {
        fanOutExecutor.execute(() -> messagingTemplate.convertAndSend("/topic/room/" + code + "/queue", snapshot.queue()));
    }
}

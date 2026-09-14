package com.tunetogether.ws;

import com.tunetogether.auth.TuneTogetherPrincipal;
import com.tunetogether.room.RoomNotFoundException;
import com.tunetogether.room.RoomRegistry;
import com.tunetogether.room.RoomRetiredException;
import com.tunetogether.ws.dto.CreateRoomResponse;
import com.tunetogether.ws.dto.JoinRoomResponse;
import com.tunetogether.ws.dto.RoomDtoMapper;
import com.tunetogether.ws.dto.RoomExistsResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * REST surface for party rooms. Requires authentication (any valid JWT, including
 * guest tokens -- no {@code ROLE_USER} requirement); the caller's identity is read via
 * the {@link Authentication} principal, never trusted from the request body.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomRestController {

    private static final long OPERATION_TIMEOUT_SECONDS = 5;

    private final RoomRegistry roomRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final Executor fanOutExecutor;

    public RoomRestController(RoomRegistry roomRegistry, SimpMessagingTemplate messagingTemplate,
                               @Qualifier("roomFanOutExecutor") Executor fanOutExecutor) {
        this.roomRegistry = roomRegistry;
        this.messagingTemplate = messagingTemplate;
        this.fanOutExecutor = fanOutExecutor;
    }

    @PostMapping
    public ResponseEntity<CreateRoomResponse> createRoom(Authentication authentication) throws Exception {
        TuneTogetherPrincipal principal = principalOf(authentication);
        RoomRegistry.CreateRoomResult result =
                roomRegistry.createRoom(principal).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateRoomResponse(result.roomCode(), result.participantId()));
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<JoinRoomResponse> joinRoom(@PathVariable String code, Authentication authentication) {
        TuneTogetherPrincipal principal = principalOf(authentication);
        try {
            RoomRegistry.JoinRoomResult result =
                    roomRegistry.joinRoom(code, principal).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // The REST call only answers the joiner; everyone already connected (the
            // host included) only learns about the new listener via this broadcast -
            // there is no other path that tells them, since join() itself is a plain
            // room-table mutation, not a STOMP-mapped action.
            var stateMessage = RoomDtoMapper.toStateMessage(result.snapshot());
            fanOutExecutor.execute(() -> messagingTemplate.convertAndSend("/topic/room/" + code + "/state", stateMessage));
            JoinRoomResponse response =
                    new JoinRoomResponse(result.participantId(), RoomDtoMapper.toSnapshotDto(result.snapshot()));
            return ResponseEntity.ok(response);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RoomNotFoundException || cause instanceof RoomRetiredException) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, cause.getMessage());
            }
            throw new IllegalStateException("Failed to join room " + code, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while joining room " + code, e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out joining room " + code, e);
        }
    }

    @GetMapping("/{code}/exists")
    public ResponseEntity<RoomExistsResponse> exists(@PathVariable String code) {
        return ResponseEntity.ok(new RoomExistsResponse(roomRegistry.find(code).isPresent()));
    }

    private static TuneTogetherPrincipal principalOf(Authentication authentication) {
        return (TuneTogetherPrincipal) authentication.getPrincipal();
    }
}

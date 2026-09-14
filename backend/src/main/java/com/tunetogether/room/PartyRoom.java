package com.tunetogether.room;

import com.tunetogether.common.TrackDto;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * A single "Party Room". Every mutation (join/leave/playback/queue/chat/retire) is
 * submitted as a task to this room's own {@link SerialExecutor} and returns a
 * {@link CompletableFuture} with the result -- by construction, exactly one thread is
 * ever running a room's own task body at a time, so the logic in each task method
 * below needs no locking of its own. Fields that are read directly from other threads
 * (the STOMP interceptor's membership check, the reaper's idle probe) are the
 * concurrency primitives called out on the class fields themselves: a {@code volatile}
 * scalar or a lock-free concurrent collection, never a plain field/HashMap.
 *
 * <p>Deliberately a plain Java object with no Spring dependency -- see the SerialExecutor
 * javadoc -- so tests can {@code new} this directly and hammer it with real concurrency
 * without a Spring context. Wired into Spring via {@link RoomConfig}.
 */
public final class PartyRoom {

    public enum PlaybackAction {PLAY, PAUSE, SEEK, TRACK_CHANGE}

    public record JoinResult(Participant participant, RoomSnapshot snapshot) {}

    public record LeaveResult(boolean participantRemoved, boolean hostChanged, boolean roomEmpty,
                               RoomSnapshot snapshot) {}

    public record ChatMessage(String senderParticipantId, String senderName, String text, boolean isEmoji,
                               long timestamp) {}

    public record RoomSnapshot(List<Participant> participants, String hostParticipantId, PlaybackState playback,
                                List<TrackDto> queue, List<ChatMessage> chatHistory) {}

    private final String code;
    private final int chatHistoryLimit;
    private final SerialExecutor actor;

    private final ConcurrentHashMap<String, Participant> participants = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<TrackDto> queue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<ChatMessage> chatLog = new ConcurrentLinkedDeque<>();

    private volatile String hostParticipantId;
    private volatile PlaybackState playbackState = PlaybackState.empty();
    private volatile long lastActivityAtMillis = System.currentTimeMillis();
    private volatile boolean retired = false;

    /** Actor-confined: mutated only from within this room's own serialized tasks. */
    private long joinOrderCounter = 0;

    /** Test seam only -- see {@link #setRetireTestHookForTests}. */
    private volatile Runnable retireTestHook;

    public PartyRoom(String code, int chatHistoryLimit, Executor sharedExecutor) {
        this.code = code;
        this.chatHistoryLimit = chatHistoryLimit;
        this.actor = new SerialExecutor(sharedExecutor);
    }

    public String code() {
        return code;
    }

    // ---------------------------------------------------------------------
    // Direct, lock-free reads -- safe to call from any thread, never round
    // -tripped through the actor. Used by the STOMP CONNECT interceptor's
    // membership check and by message handlers resolving a display name.
    // ---------------------------------------------------------------------

    public boolean hasParticipant(String participantId) {
        return participants.containsKey(participantId);
    }

    public boolean isRetired() {
        return retired;
    }

    public String currentHostParticipantId() {
        return hostParticipantId;
    }

    public Optional<String> participantDisplayName(String participantId) {
        return Optional.ofNullable(participants.get(participantId)).map(Participant::name);
    }

    /**
     * Test seam only (package-private): builds a snapshot directly, without going
     * through the actor. Only safe to call once the room is known to be quiescent
     * (e.g. after every previously-submitted future has completed) -- otherwise the
     * read may be inconsistent across the participants/queue/chat collections, since
     * each is only weakly-consistent on its own.
     */
    RoomSnapshot currentSnapshotForTests() {
        return buildSnapshot();
    }

    // ---------------------------------------------------------------------
    // Mutating operations. Every one of these funnels through the actor.
    // ---------------------------------------------------------------------

    /** Creates a brand-new participant identity in this room (REST create/join). */
    public CompletableFuture<JoinResult> join(String displayName) {
        CompletableFuture<JoinResult> future = new CompletableFuture<>();
        actor.execute(() -> {
            if (retired) {
                future.completeExceptionally(new RoomRetiredException(code));
                return;
            }
            String participantId = UUID.randomUUID().toString();
            long order = joinOrderCounter++;
            Participant participant = new Participant(participantId, displayName, null, order);
            participants.put(participantId, participant);
            if (hostParticipantId == null) {
                hostParticipantId = participantId;
            }
            touch();
            future.complete(new JoinResult(participant, buildSnapshot()));
        });
        return future;
    }

    /**
     * Binds (or rebinds, on reconnect) a live STOMP session id onto an already-existing
     * participant. Called by the CONNECT interceptor once membership has been verified.
     * Completes with {@code false} (never exceptionally) if the participant is unknown
     * or the room has been retired -- this is bookkeeping, not an authorization gate.
     */
    public CompletableFuture<Boolean> connectSession(String participantId, String sessionId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        actor.execute(() -> {
            if (retired) {
                future.complete(false);
                return;
            }
            Participant existing = participants.get(participantId);
            if (existing == null) {
                future.complete(false);
                return;
            }
            participants.put(participantId, existing.withSessionId(sessionId));
            touch();
            future.complete(true);
        });
        return future;
    }

    /**
     * Removes a participant, but only if {@code sessionId} still matches the session
     * currently bound to that participant -- a no-op otherwise. This is what stops a
     * late-arriving disconnect for a session already superseded by a fast reconnect
     * (same participantId, new sessionId) from evicting the reconnected participant.
     */
    public CompletableFuture<LeaveResult> leave(String participantId, String sessionId) {
        CompletableFuture<LeaveResult> future = new CompletableFuture<>();
        actor.execute(() -> {
            if (retired) {
                future.completeExceptionally(new RoomRetiredException(code));
                return;
            }
            Participant existing = participants.get(participantId);
            if (existing == null || !Objects.equals(existing.sessionId(), sessionId)) {
                future.complete(new LeaveResult(false, false, participants.isEmpty(), buildSnapshot()));
                return;
            }
            participants.remove(participantId);
            boolean hostChanged = false;
            if (participantId.equals(hostParticipantId)) {
                hostParticipantId = electNewHost();
                hostChanged = true;
            }
            touch();
            future.complete(new LeaveResult(true, hostChanged, participants.isEmpty(), buildSnapshot()));
        });
        return future;
    }

    public CompletableFuture<RoomSnapshot> setPlayback(String requesterParticipantId, PlaybackAction action,
                                                         TrackDto track, Double positionSeconds) {
        CompletableFuture<RoomSnapshot> future = new CompletableFuture<>();
        actor.execute(() -> {
            if (retired) {
                future.completeExceptionally(new RoomRetiredException(code));
                return;
            }
            if (!Objects.equals(requesterParticipantId, hostParticipantId)) {
                future.completeExceptionally(new NotHostException(requesterParticipantId, code));
                return;
            }
            PlaybackState current = playbackState;
            long now = System.currentTimeMillis();
            PlaybackState next = switch (action) {
                case PLAY -> new PlaybackState(current.currentTrack(), true,
                        positionSeconds != null ? positionSeconds : current.currentTimeSeconds(), now);
                case PAUSE -> new PlaybackState(current.currentTrack(), false,
                        positionSeconds != null ? positionSeconds : current.currentTimeSeconds(), now);
                case SEEK -> new PlaybackState(current.currentTrack(), current.isPlaying(),
                        positionSeconds != null ? positionSeconds : current.currentTimeSeconds(), now);
                case TRACK_CHANGE -> new PlaybackState(track, true,
                        positionSeconds != null ? positionSeconds : 0.0, now);
            };
            playbackState = next;
            touch();
            future.complete(buildSnapshot());
        });
        return future;
    }

    /** Host-only lightweight position update: swaps just the time/timestamp fields. */
    public CompletableFuture<RoomSnapshot> tick(String requesterParticipantId, double positionSeconds) {
        CompletableFuture<RoomSnapshot> future = new CompletableFuture<>();
        actor.execute(() -> {
            if (retired) {
                future.completeExceptionally(new RoomRetiredException(code));
                return;
            }
            if (!Objects.equals(requesterParticipantId, hostParticipantId)) {
                future.completeExceptionally(new NotHostException(requesterParticipantId, code));
                return;
            }
            PlaybackState current = playbackState;
            playbackState = new PlaybackState(current.currentTrack(), current.isPlaying(), positionSeconds,
                    System.currentTimeMillis());
            touch();
            future.complete(buildSnapshot());
        });
        return future;
    }

    /** Anyone in the room may append to the shared queue. */
    public CompletableFuture<RoomSnapshot> addToQueue(TrackDto track) {
        CompletableFuture<RoomSnapshot> future = new CompletableFuture<>();
        actor.execute(() -> {
            if (retired) {
                future.completeExceptionally(new RoomRetiredException(code));
                return;
            }
            queue.offerLast(track);
            touch();
            future.complete(buildSnapshot());
        });
        return future;
    }

    /** Host-only: pops the queue head and makes it the new current (playing) track. */
    public CompletableFuture<RoomSnapshot> popQueue(String requesterParticipantId) {
        CompletableFuture<RoomSnapshot> future = new CompletableFuture<>();
        actor.execute(() -> {
            if (retired) {
                future.completeExceptionally(new RoomRetiredException(code));
                return;
            }
            if (!Objects.equals(requesterParticipantId, hostParticipantId)) {
                future.completeExceptionally(new NotHostException(requesterParticipantId, code));
                return;
            }
            TrackDto next = queue.pollFirst();
            if (next != null) {
                playbackState = new PlaybackState(next, true, 0.0, System.currentTimeMillis());
            }
            touch();
            future.complete(buildSnapshot());
        });
        return future;
    }

    public CompletableFuture<ChatMessage> postChat(String participantId, String senderName, String text,
                                                     boolean isEmoji) {
        CompletableFuture<ChatMessage> future = new CompletableFuture<>();
        actor.execute(() -> {
            if (retired) {
                future.completeExceptionally(new RoomRetiredException(code));
                return;
            }
            ChatMessage message = new ChatMessage(participantId, senderName, text, isEmoji,
                    System.currentTimeMillis());
            chatLog.offerLast(message);
            while (chatLog.size() > chatHistoryLimit) {
                chatLog.pollFirst();
            }
            touch();
            future.complete(message);
        });
        return future;
    }

    /**
     * Checks whether this room is idle (empty, or idle past {@code idleTimeout} with
     * zero activity) and, if so, retires it and removes it from the registry using
     * {@code registryConditionalRemove} -- which is literally
     * {@code room -> registry.rooms.remove(code, room)} -- all inside the SAME
     * serialized actor task. Because {@link SerialExecutor} runs tasks strictly FIFO,
     * a join submitted before this task runs sees a non-idle/non-retired room (or this
     * no-ops if not actually idle); a join submitted after this task completes always
     * hits the {@code retired} guard and fails with {@link RoomRetiredException}.
     * There is no interleaving that leaves a participant added to a room that is
     * simultaneously unreachable via the registry.
     *
     * @return a future completing with {@code true} iff this call is the one that
     * retired and deregistered the room, {@code false} if it was not idle or was
     * already retired by an earlier task.
     */
    public CompletableFuture<Boolean> retireIfIdle(Duration idleTimeout, Predicate<PartyRoom> registryConditionalRemove) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        actor.execute(() -> {
            Runnable hook = retireTestHook;
            if (hook != null) {
                hook.run();
            }
            if (retired) {
                future.complete(false);
                return;
            }
            boolean idle = participants.isEmpty()
                    || (System.currentTimeMillis() - lastActivityAtMillis) >= idleTimeout.toMillis();
            if (!idle) {
                future.complete(false);
                return;
            }
            retired = true;
            boolean removed = registryConditionalRemove.test(this);
            future.complete(removed);
        });
        return future;
    }

    /**
     * Test seam only (package-private, same-package tests only): lets a test pause a
     * {@link #retireIfIdle} task right as it starts running -- before it touches any
     * state -- via e.g. a {@link java.util.concurrent.CountDownLatch}, so a concurrent
     * join submitted from another thread can be deterministically interleaved behind
     * it and the outcome asserted.
     */
    void setRetireTestHookForTests(Runnable hook) {
        this.retireTestHook = hook;
    }

    private String electNewHost() {
        return participants.values().stream()
                .min(Comparator.comparingLong(Participant::joinOrder))
                .map(Participant::id)
                .orElse(null);
    }

    private void touch() {
        lastActivityAtMillis = System.currentTimeMillis();
    }

    private RoomSnapshot buildSnapshot() {
        List<Participant> participantList = new ArrayList<>(participants.values());
        participantList.sort(Comparator.comparingLong(Participant::joinOrder));
        List<TrackDto> queueList = new ArrayList<>(queue);
        List<ChatMessage> chatList = new ArrayList<>(chatLog);
        return new RoomSnapshot(participantList, hostParticipantId, playbackState, queueList, chatList);
    }
}

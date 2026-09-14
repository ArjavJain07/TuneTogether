package com.tunetogether.room;

import com.tunetogether.auth.TuneTogetherPrincipal;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * The room table: a lock-free map from room code to {@link PartyRoom}, plus the logic
 * for generating collision-free codes and tearing down idle rooms. Deliberately a
 * plain Java object with no Spring dependency -- see {@link SerialExecutor}'s javadoc
 * for why -- so tests can {@code new} this directly. Wired into Spring as a singleton
 * bean by {@link RoomConfig}.
 */
public final class RoomRegistry {

    /** Visually-unambiguous alphabet (no 0/O/1/I) for generated room codes. */
    private static final String DEFAULT_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 100_000;

    private final ConcurrentHashMap<String, PartyRoom> rooms = new ConcurrentHashMap<>();
    private final int chatHistoryLimit;
    private final Duration idleTimeout;
    private final Executor sharedExecutor;
    private final Supplier<String> codeGenerator;

    public RoomRegistry(int codeLength, int chatHistoryLimit, Duration idleTimeout, Executor sharedExecutor) {
        this(chatHistoryLimit, idleTimeout, sharedExecutor, () -> randomCode(codeLength));
    }

    /** Test seam: inject a small/biased code generator to force frequent collisions. */
    RoomRegistry(int chatHistoryLimit, Duration idleTimeout, Executor sharedExecutor, Supplier<String> codeGenerator) {
        this.chatHistoryLimit = chatHistoryLimit;
        this.idleTimeout = idleTimeout;
        this.sharedExecutor = sharedExecutor;
        this.codeGenerator = codeGenerator;
    }

    static RoomRegistry withCodeGeneratorForTests(int chatHistoryLimit, Duration idleTimeout, Executor sharedExecutor,
                                                   Supplier<String> codeGenerator) {
        return new RoomRegistry(chatHistoryLimit, idleTimeout, sharedExecutor, codeGenerator);
    }

    private static String randomCode(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(DEFAULT_CODE_ALPHABET.charAt(random.nextInt(DEFAULT_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    public Optional<PartyRoom> find(String code) {
        return Optional.ofNullable(rooms.get(code));
    }

    /** Snapshot of currently-registered rooms, for the backstop sweep. */
    public Collection<PartyRoom> allRooms() {
        return rooms.values();
    }

    public CompletableFuture<CreateRoomResult> createRoom(TuneTogetherPrincipal principal) {
        PartyRoom room = registerNewRoom();
        return room.join(principal.displayName())
                .thenApply(joinResult -> new CreateRoomResult(room.code(), joinResult.participant().id()));
    }

    public CompletableFuture<JoinRoomResult> joinRoom(String code, TuneTogetherPrincipal principal) {
        PartyRoom room = rooms.get(code);
        if (room == null) {
            return CompletableFuture.failedFuture(new RoomNotFoundException(code));
        }
        return room.join(principal.displayName())
                .thenApply(joinResult -> new JoinRoomResult(joinResult.participant().id(), joinResult.snapshot()));
    }

    /**
     * Retires {@code room} if idle and, atomically with that same decision, removes it
     * from this table -- the conditional-remove predicate passed down to
     * {@link PartyRoom#retireIfIdle} is literally {@code r -> rooms.remove(r.code(), r)}.
     * Called both eagerly (chained after any leave/disconnect that might have emptied
     * a room) and periodically by {@link RoomReaper}'s backstop sweep.
     */
    public CompletableFuture<Boolean> reapIfIdle(PartyRoom room) {
        return room.retireIfIdle(idleTimeout, r -> rooms.remove(r.code(), r));
    }

    private PartyRoom registerNewRoom() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = codeGenerator.get();
            PartyRoom candidate = new PartyRoom(code, chatHistoryLimit, sharedExecutor);
            PartyRoom existing = rooms.computeIfAbsent(code, c -> candidate);
            if (existing == candidate) {
                return candidate;
            }
            // Lost the race on a rare collision (or the code space is simply exhausted
            // for now) -- the mapping function above was never invoked for this attempt,
            // so no room leaked into any half-registered state; just retry with a fresh code.
        }
        throw new IllegalStateException(
                "Could not allocate a free room code after " + MAX_CODE_GENERATION_ATTEMPTS + " attempts");
    }

    public record CreateRoomResult(String roomCode, String participantId) {}

    public record JoinRoomResult(String participantId, PartyRoom.RoomSnapshot snapshot) {}
}

package com.tunetogether.room;

import com.tunetogether.auth.TuneTogetherPrincipal;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic (not pure-fuzz) test for the reap-vs-join race described on
 * {@link PartyRoom#retireIfIdle}: a room's "am I idle -> remove myself from the
 * registry" decision and the actual registry removal happen inside the SAME
 * serialized actor task, so a join can never be silently added to a room that is
 * simultaneously unreachable via the registry -- it either lands in a room the
 * registry still holds, or it fails cleanly with {@link RoomRetiredException}.
 *
 * <p>Uses the package-private {@code setRetireTestHookForTests} seam to pause a
 * retire task right as it starts running (before it touches any state), so a
 * concurrent join submitted from another thread can be deterministically forced to
 * queue up behind it and the resulting outcome asserted -- rather than hoping a fuzz
 * test happens to hit the interesting interleaving.
 */
class ReapVsJoinRaceTest {

    private static TuneTogetherPrincipal guestPrincipal(String subjectId, String displayName) {
        return new TuneTogetherPrincipal(subjectId, null, displayName, true, List.of());
    }

    @Test
    void joinQueuedBehindAnInFlightReapFailsCleanlyAndRoomStaysDeregistered() throws Exception {
        RoomRegistry registry = new RoomRegistry(6, 200, Duration.ofMinutes(30),
                Executors.newVirtualThreadPerTaskExecutor());

        RoomRegistry.CreateRoomResult created =
                registry.createRoom(guestPrincipal("host-sub", "Host")).get(5, TimeUnit.SECONDS);
        String code = created.roomCode();
        PartyRoom room = registry.find(code).orElseThrow();

        // Empty the room out so retireIfIdle's idle check (participants.isEmpty()) is
        // trivially true regardless of the configured idle timeout.
        room.leave(created.participantId(), null).get(5, TimeUnit.SECONDS);

        CountDownLatch reapStarted = new CountDownLatch(1);
        CountDownLatch releaseReap = new CountDownLatch(1);
        room.setRetireTestHookForTests(() -> {
            reapStarted.countDown();
            try {
                releaseReap.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Kick off the reap; it immediately starts running on the shared delegate pool
        // and blocks inside the hook, without blocking this (the calling) thread.
        CompletableFuture<Boolean> reapFuture = registry.reapIfIdle(room);
        assertTrue(reapStarted.await(5, TimeUnit.SECONDS), "the reap task must have started running");

        // While the reap is in flight (dequeued, actively running, but paused before
        // mutating any state), submit a join. Because SerialExecutor is strictly FIFO
        // and the reap task is still the active one, this join is queued behind it and
        // cannot start until the reap task completes.
        CompletableFuture<PartyRoom.JoinResult> joinFuture = room.join("late-joiner");

        releaseReap.countDown();

        assertTrue(reapFuture.get(5, TimeUnit.SECONDS), "the reap must be the one that retires and deregisters the room");
        assertFalse(registry.find(code).isPresent(), "the room must no longer be reachable via the registry");

        ExecutionException ex = assertThrows(ExecutionException.class, () -> joinFuture.get(5, TimeUnit.SECONDS));
        assertInstanceOf(RoomRetiredException.class, ex.getCause(),
                "a join queued behind an in-flight reap must fail with RoomRetiredException, never succeed silently");
    }

    @Test
    void joinSubmittedBeforeReapEvenStartsSucceedsNormally() throws Exception {
        RoomRegistry registry = new RoomRegistry(6, 200, Duration.ofMinutes(30),
                Executors.newVirtualThreadPerTaskExecutor());

        RoomRegistry.CreateRoomResult created =
                registry.createRoom(guestPrincipal("host-sub", "Host")).get(5, TimeUnit.SECONDS);
        String code = created.roomCode();
        PartyRoom room = registry.find(code).orElseThrow();

        // Room still has its host as a participant, so it is not idle: reapIfIdle must
        // no-op (never retiring a room that a join elsewhere is relying on).
        CompletableFuture<PartyRoom.JoinResult> joinFuture = room.join("second-participant");
        PartyRoom.JoinResult joinResult = joinFuture.get(5, TimeUnit.SECONDS);
        assertEquals(2, joinResult.snapshot().participants().size());

        boolean reaped = registry.reapIfIdle(room).get(5, TimeUnit.SECONDS);
        assertFalse(reaped, "a non-idle room must not be retired");
        assertTrue(registry.find(code).isPresent(), "the room must remain registered");
    }
}

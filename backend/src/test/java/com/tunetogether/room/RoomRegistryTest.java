package com.tunetogether.room;

import com.tunetogether.auth.TuneTogetherPrincipal;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomRegistryTest {

    private static TuneTogetherPrincipal guestPrincipal(String subjectId, String displayName) {
        return new TuneTogetherPrincipal(subjectId, null, displayName, true, List.of());
    }

    // 5. Room-code generation under forced collisions: a deliberately tiny code pool
    // (barely bigger than the number of rooms being created) forces very frequent
    // collisions, but concurrent createRoom calls must still yield exactly N distinct
    // rooms -- none silently overwritten (which computeIfAbsent's atomicity guarantees
    // as long as the retry loop itself is correct).
    @Test
    void codeCollisionsUnderConcurrencyYieldExactlyNDistinctRooms() throws Exception {
        int n = 300;
        int poolSize = n + 20; // deliberately tight relative to n -> frequent collisions
        String[] pool = new String[poolSize];
        for (int i = 0; i < poolSize; i++) {
            pool[i] = String.format("C%05d", i);
        }
        Supplier<String> collisionProneGenerator = () -> pool[ThreadLocalRandom.current().nextInt(poolSize)];

        Executor sharedExecutor = Executors.newVirtualThreadPerTaskExecutor();
        RoomRegistry registry = RoomRegistry.withCodeGeneratorForTests(
                200, Duration.ofMinutes(30), sharedExecutor, collisionProneGenerator);

        ExecutorService callerPool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<RoomRegistry.CreateRoomResult>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int idx = i;
            CompletableFuture<RoomRegistry.CreateRoomResult> future = new CompletableFuture<>();
            futures.add(future);
            callerPool.execute(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                registry.createRoom(guestPrincipal("sub-" + idx, "user-" + idx))
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                future.completeExceptionally(ex);
                            } else {
                                future.complete(result);
                            }
                        });
            });
        }
        startLatch.countDown();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

        Set<String> codes = new HashSet<>();
        for (var f : futures) {
            codes.add(f.get().roomCode());
        }
        assertEquals(n, codes.size(), "every concurrent createRoom must land on a distinct, unstolen code");
        assertEquals(n, registry.allRooms().size(), "the registry must end up with exactly N rooms");
        callerPool.shutdown();
    }

    @Test
    void createThenJoinReturnsASnapshotWithBothParticipants() throws Exception {
        RoomRegistry registry = new RoomRegistry(6, 200, Duration.ofMinutes(30),
                Executors.newVirtualThreadPerTaskExecutor());

        RoomRegistry.CreateRoomResult created =
                registry.createRoom(guestPrincipal("host-sub", "Host")).get(5, TimeUnit.SECONDS);
        RoomRegistry.JoinRoomResult joined =
                registry.joinRoom(created.roomCode(), guestPrincipal("guest-sub", "Guest")).get(5, TimeUnit.SECONDS);

        assertEquals(2, joined.snapshot().participants().size());
        assertEquals(created.participantId(), joined.snapshot().hostParticipantId());
        assertTrue(registry.find(created.roomCode()).isPresent());
    }

    @Test
    void joiningAnUnknownCodeFailsWithRoomNotFoundException() {
        RoomRegistry registry = new RoomRegistry(6, 200, Duration.ofMinutes(30),
                Executors.newVirtualThreadPerTaskExecutor());

        var future = registry.joinRoom("NOPE01", guestPrincipal("sub", "Someone"));
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof RoomNotFoundException);
    }
}

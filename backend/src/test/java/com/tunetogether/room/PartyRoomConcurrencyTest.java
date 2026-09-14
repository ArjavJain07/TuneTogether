package com.tunetogether.room;

import com.tunetogether.common.TrackDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hammers a single PartyRoom's actor with real concurrency (many virtual threads,
 * genuinely raced submissions) and asserts on the settled result -- never on timing.
 * Every wait is a bounded {@code CompletableFuture.get(timeout, unit)}, never a
 * {@code Thread.sleep} used to "wait for" async work to finish.
 */
class PartyRoomConcurrencyTest {

    private static PartyRoom newRoom(String code, int chatHistoryLimit) {
        return new PartyRoom(code, chatHistoryLimit, Executors.newVirtualThreadPerTaskExecutor());
    }

    private static TrackDto sampleTrack(String id) {
        return new TrackDto(id, "title-" + id, "artist", "album", "art", 180, "preview", "uri", "yt-" + id);
    }

    // 1. Many concurrent joins (200 threads) -> no duplicate participant ids.
    @Test
    void manyConcurrentJoinsProduceNoDuplicateParticipantIds() throws Exception {
        PartyRoom room = newRoom("JOIN01", 200);
        int n = 200;
        ExecutorService callerPool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<PartyRoom.JoinResult>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int idx = i;
            CompletableFuture<PartyRoom.JoinResult> future = new CompletableFuture<>();
            futures.add(future);
            callerPool.execute(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                room.join("user-" + idx).whenComplete((result, ex) -> {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                    } else {
                        future.complete(result);
                    }
                });
            });
        }
        startLatch.countDown();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        Set<String> ids = new HashSet<>();
        for (var f : futures) {
            ids.add(f.get().participant().id());
        }
        assertEquals(n, ids.size(), "every join must produce a unique participant id");
        callerPool.shutdown();
    }

    // 2. Randomized concurrent join/leave/host-disconnect interleaving -> after each
    // settled batch, hostParticipantId is null iff participants is empty, and
    // otherwise names exactly one currently-present participant.
    @Test
    void randomizedJoinLeaveInterleavingKeepsHostInvariant() throws Exception {
        PartyRoom room = newRoom("JOIN02", 200);
        ExecutorService callerPool = Executors.newVirtualThreadPerTaskExecutor();
        Random random = new Random(20260914L);
        List<Participant> alive = new ArrayList<>();

        for (int round = 0; round < 25; round++) {
            int joins = 1 + random.nextInt(6);
            int maxLeaves = Math.min(alive.size(), 5);
            int leaves = maxLeaves == 0 ? 0 : random.nextInt(maxLeaves + 1);

            List<Participant> shuffled = new ArrayList<>(alive);
            Collections.shuffle(shuffled, random);
            List<Participant> leaving = shuffled.subList(0, leaves);

            List<CompletableFuture<PartyRoom.JoinResult>> joinFutures = new ArrayList<>();
            List<CompletableFuture<PartyRoom.LeaveResult>> leaveFutures = new ArrayList<>();
            CountDownLatch startLatch = new CountDownLatch(1);

            for (int i = 0; i < joins; i++) {
                CompletableFuture<PartyRoom.JoinResult> future = new CompletableFuture<>();
                joinFutures.add(future);
                callerPool.execute(() -> {
                    await(startLatch);
                    room.join("p").whenComplete((r, ex) -> complete(future, r, ex));
                });
            }
            for (Participant p : leaving) {
                CompletableFuture<PartyRoom.LeaveResult> future = new CompletableFuture<>();
                leaveFutures.add(future);
                callerPool.execute(() -> {
                    await(startLatch);
                    room.leave(p.id(), p.sessionId()).whenComplete((r, ex) -> complete(future, r, ex));
                });
            }
            startLatch.countDown();

            List<CompletableFuture<?>> all = new ArrayList<>();
            all.addAll(joinFutures);
            all.addAll(leaveFutures);
            CompletableFuture.allOf(all.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

            alive.removeAll(leaving);
            for (var jf : joinFutures) {
                alive.add(jf.get().participant());
            }

            String hostId = room.currentHostParticipantId();
            if (alive.isEmpty()) {
                assertNull(hostId, "host must be null once the room is empty");
            } else {
                assertNotNull(hostId, "host must be set while participants remain");
                long matches = alive.stream().filter(p -> p.id().equals(hostId)).count();
                assertEquals(1, matches, "host must name exactly one currently-present participant");
            }
        }
        callerPool.shutdown();
    }

    // 3. 500 concurrent queue pushes tagged by thread index -> final queue is exactly
    // those 500 unique ids (set equality, not just size).
    @Test
    void concurrentQueuePushesAllLandExactlyOnce() throws Exception {
        PartyRoom room = newRoom("QUEUE01", 200);
        int n = 500;
        ExecutorService callerPool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<Void>> done = new ArrayList<>();
        Set<String> expectedIds = newConcurrentSet();
        for (int i = 0; i < n; i++) {
            String trackId = "track-" + i;
            expectedIds.add(trackId);
            CompletableFuture<Void> future = new CompletableFuture<>();
            done.add(future);
            callerPool.execute(() -> {
                await(startLatch);
                room.addToQueue(sampleTrack(trackId)).whenComplete((r, ex) -> complete(future, null, ex));
            });
        }
        startLatch.countDown();
        CompletableFuture.allOf(done.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        List<TrackDto> finalQueue = room.currentSnapshotForTests().queue();
        Set<String> actualIds = finalQueue.stream().map(TrackDto::id).collect(Collectors.toSet());
        assertEquals(n, finalQueue.size(), "no push should be lost or duplicated");
        assertEquals(expectedIds, actualIds, "the queue must contain exactly the pushed ids");
        callerPool.shutdown();
    }

    // 4. Concurrent chat appends past the cap -> final size equals the cap, no corruption.
    @Test
    void concurrentChatAppendsPastCapRespectTheCap() throws Exception {
        int cap = 50;
        PartyRoom room = newRoom("CHAT01", cap);
        int n = 500;
        ExecutorService callerPool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        List<CompletableFuture<Void>> done = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int idx = i;
            CompletableFuture<Void> future = new CompletableFuture<>();
            done.add(future);
            callerPool.execute(() -> {
                await(startLatch);
                room.postChat("p" + idx, "name" + idx, "message " + idx, false)
                        .whenComplete((r, ex) -> complete(future, null, ex));
            });
        }
        startLatch.countDown();
        CompletableFuture.allOf(done.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        List<PartyRoom.ChatMessage> chatHistory = room.currentSnapshotForTests().chatHistory();
        assertEquals(cap, chatHistory.size(), "chat history must be trimmed to the configured cap");
        Set<String> distinctSenders = chatHistory.stream()
                .map(PartyRoom.ChatMessage::senderParticipantId)
                .collect(Collectors.toSet());
        assertEquals(cap, distinctSenders.size(), "no corruption: every retained message must be distinct");
        callerPool.shutdown();
    }

    // 6. Session fencing: join with S1, "reconnect" with S2, then a stale leave(S1)
    // must not evict the reconnected participant.
    @Test
    void staleDisconnectAfterFastReconnectDoesNotEvictParticipant() throws Exception {
        PartyRoom room = newRoom("SESSION01", 200);
        PartyRoom.JoinResult joinResult = room.join("host").get(5, TimeUnit.SECONDS);
        String participantId = joinResult.participant().id();

        assertTrue(room.connectSession(participantId, "S1").get(5, TimeUnit.SECONDS));
        assertTrue(room.connectSession(participantId, "S2").get(5, TimeUnit.SECONDS), "reconnect must rebind cleanly");

        PartyRoom.LeaveResult staleLeave = room.leave(participantId, "S1").get(5, TimeUnit.SECONDS);
        assertFalse(staleLeave.participantRemoved(), "a stale session's leave must be a no-op after reconnect");
        assertTrue(room.hasParticipant(participantId), "the reconnected participant must still be present");

        PartyRoom.LeaveResult realLeave = room.leave(participantId, "S2").get(5, TimeUnit.SECONDS);
        assertTrue(realLeave.participantRemoved(), "the CURRENT session's leave must still work");
        assertFalse(room.hasParticipant(participantId));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static <T> void complete(CompletableFuture<T> future, T value, Throwable ex) {
        if (ex != null) {
            future.completeExceptionally(ex);
        } else {
            future.complete(value);
        }
    }

    private static Set<String> newConcurrentSet() {
        return java.util.concurrent.ConcurrentHashMap.newKeySet();
    }
}

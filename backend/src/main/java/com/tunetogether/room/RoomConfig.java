package com.tunetogether.room;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Wires the plain-Java room engine ({@link RoomRegistry}/{@link PartyRoom}/
 * {@link SerialExecutor} -- deliberately Spring-free so tests can {@code new} them
 * directly and hammer them with real concurrency without a Spring context) into the
 * Spring context as singletons.
 */
@Configuration
@EnableScheduling
public class RoomConfig {

    /** Shared delegate pool for every room's per-room {@link SerialExecutor}. */
    @Bean(name = "roomActorSharedExecutor")
    public Executor roomActorSharedExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Dedicated pool for fanning broadcasts out to {@link org.springframework.messaging.simp.SimpMessagingTemplate}
     * after a room mutation completes -- kept separate from the room actors and the
     * STOMP inbound channel so a slow/backpressured subscriber can never stall a
     * room's write path.
     */
    @Bean(name = "roomFanOutExecutor")
    public Executor roomFanOutExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public RoomRegistry roomRegistry(
            @Value("${app.room.code-length}") int codeLength,
            @Value("${app.room.chat-history-limit}") int chatHistoryLimit,
            @Value("${app.room.idle-timeout-minutes}") long idleTimeoutMinutes,
            @Qualifier("roomActorSharedExecutor") Executor roomActorSharedExecutor) {
        return new RoomRegistry(codeLength, chatHistoryLimit, Duration.ofMinutes(idleTimeoutMinutes),
                roomActorSharedExecutor);
    }

    @Bean
    public RoomReaper roomReaper(RoomRegistry roomRegistry) {
        return new RoomReaper(roomRegistry);
    }
}

package com.tunetogether.music;

import com.tunetogether.common.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal in-memory fixed-window rate limiter keyed by JWT principal id (not
 * IP - survives NAT and works identically for guests and registered users), so
 * one client can't exhaust the shared YouTube API key's quota.
 * <p>
 * {@link ConcurrentHashMap#compute} serializes concurrent calls for the same
 * key via its per-bin locking, so incrementing-and-checking a principal's
 * window is atomic without any explicit lock here.
 */
@Component
public class MusicRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;

    private record Window(long windowStartMillis, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limitPerWindow;

    public MusicRateLimiter(@Value("${app.music.rate-limit-per-minute}") int limitPerWindow) {
        this.limitPerWindow = limitPerWindow;
    }

    /** Throws {@link RateLimitExceededException} if the principal has exceeded its quota. */
    public void checkAndRecord(String principalId) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(principalId, (key, existing) -> {
            if (existing == null || now - existing.windowStartMillis() >= WINDOW_MILLIS) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (window.count().get() > limitPerWindow) {
            long retryAfterSeconds = Math.max(1, (WINDOW_MILLIS - (now - window.windowStartMillis())) / 1000);
            throw new RateLimitExceededException("Music search rate limit exceeded", retryAfterSeconds);
        }
    }
}

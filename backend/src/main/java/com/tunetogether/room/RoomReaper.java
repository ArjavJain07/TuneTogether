package com.tunetogether.room;

/**
 * Backstop sweep: periodically asks every currently-registered room whether it is
 * idle (empty, or idle past the configured timeout with zero activity) and, if so,
 * retires and deregisters it via {@link RoomRegistry#reapIfIdle}. This complements the
 * eager reap chained after every leave/disconnect -- it catches rooms that go idle
 * without ever seeing a clean leave, e.g. every participant's browser tab or process
 * dying without a disconnect frame ever arriving (the "zombie session" case).
 *
 * <p>Deliberately a plain Java object; the {@code @Scheduled} trigger lives on a small
 * Spring bean in the {@code ws} package that simply calls {@link #sweepOnce()}.
 */
public final class RoomReaper {

    private final RoomRegistry registry;

    public RoomReaper(RoomRegistry registry) {
        this.registry = registry;
    }

    /**
     * Fire-and-forget sweep: each room's {@code retireIfIdle} is independently
     * serialized on that room's own actor, so sweeping many rooms never blocks on any
     * one of them.
     */
    public void sweepOnce() {
        for (PartyRoom room : registry.allRooms()) {
            registry.reapIfIdle(room);
        }
    }
}

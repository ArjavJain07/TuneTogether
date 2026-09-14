package com.tunetogether.room;

/**
 * A member of a {@link PartyRoom}. {@code joinOrder} is the monotonically increasing
 * counter used to elect the next host on departure (lowest join-order among the
 * remaining participants). {@code sessionId} is the currently bound STOMP session id
 * (assigned via {@link PartyRoom#connectSession}), used to fence a stale/superseded
 * disconnect event against a participant that has already reconnected under a new
 * session -- see {@link PartyRoom#leave}.
 *
 * <p>Immutable by design: every "mutation" (rebinding the session) produces a fresh
 * record via {@link #withSessionId}, and the enclosing {@code ConcurrentHashMap} entry
 * is replaced wholesale by the room's single-writer actor. That avoids any reader ever
 * observing a half-updated participant.
 */
public record Participant(String id, String name, String sessionId, long joinOrder) {

    public Participant withSessionId(String newSessionId) {
        return new Participant(id, name, newSessionId, joinOrder);
    }
}

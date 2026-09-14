package com.tunetogether.room;

/**
 * Thrown when a participant who is not the room's current host attempts a host-only
 * operation (playback control, position tick, queue pop). "Who is host" is always
 * resolved server-side from {@link PartyRoom#currentHostParticipantId()} inside the
 * room's own actor task -- never trusted from a client-supplied field.
 */
public class NotHostException extends RuntimeException {

    public NotHostException(String participantId, String roomCode) {
        super("Participant " + participantId + " is not the host of room " + roomCode);
    }
}

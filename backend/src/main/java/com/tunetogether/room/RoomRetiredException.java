package com.tunetogether.room;

/**
 * Thrown when a mutating operation is submitted to a {@link PartyRoom} after it has
 * already been retired (removed, or in the process of being removed, from the
 * {@link RoomRegistry}). See the reap-vs-join race notes on {@link PartyRoom#retireIfIdle}
 * for why this is the only outcome ever possible for a "late" join -- never a silent
 * add to an orphaned room.
 */
public class RoomRetiredException extends RuntimeException {

    public RoomRetiredException(String roomCode) {
        super("Room " + roomCode + " has been retired");
    }
}

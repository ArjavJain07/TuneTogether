package com.tunetogether.room;

/** Thrown when a room code does not correspond to any currently registered room. */
public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(String roomCode) {
        super("Room " + roomCode + " does not exist");
    }
}

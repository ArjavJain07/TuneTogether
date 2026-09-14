package com.tunetogether.ws.dto;

/** {@code 200} response body of {@code POST /api/rooms/{code}/join}. */
public record JoinRoomResponse(String participantId, RoomSnapshotDTO snapshot) {}

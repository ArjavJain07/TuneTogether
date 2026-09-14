package com.tunetogether.ws.dto;

/** {@code 201} response body of {@code POST /api/rooms}. */
public record CreateRoomResponse(String roomCode, String participantId) {}

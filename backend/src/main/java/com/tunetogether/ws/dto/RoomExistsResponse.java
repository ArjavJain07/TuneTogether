package com.tunetogether.ws.dto;

/** Response body of {@code GET /api/rooms/{code}/exists}. */
public record RoomExistsResponse(boolean exists) {}

package com.tunetogether.ws.dto;

/** Wire shape for a room participant. {@code isHost} is computed at serialization time. */
public record ParticipantDTO(String id, String name, boolean isHost) {}

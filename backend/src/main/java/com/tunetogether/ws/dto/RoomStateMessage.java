package com.tunetogether.ws.dto;

import java.util.List;

/** Broadcast on {@code /topic/room/{code}/state} after every join/leave/host-change/playback change. */
public record RoomStateMessage(List<ParticipantDTO> participants, String hostParticipantId,
                                PlaybackStateDTO playback) {}

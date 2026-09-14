package com.tunetogether.ws.dto;

import com.tunetogether.common.TrackDto;

import java.util.List;

/**
 * Everything a freshly-joining client needs in one shot: participants, host id,
 * playback state, the current queue, and the last N chat messages (N =
 * {@code app.room.chat-history-limit}, or fewer if the room has less history).
 */
public record RoomSnapshotDTO(List<ParticipantDTO> participants, String hostParticipantId,
                               PlaybackStateDTO playback, List<TrackDto> queue,
                               List<ChatMessageDto> chatHistory) {}

package com.tunetogether.ws.dto;

import com.tunetogether.room.Participant;
import com.tunetogether.room.PartyRoom;
import com.tunetogether.room.PlaybackState;

import java.util.List;

/**
 * Translates plain-Java room-package types ({@link PartyRoom.RoomSnapshot},
 * {@link Participant}, {@link PlaybackState}, {@link PartyRoom.ChatMessage}) into the
 * wire DTOs the REST/STOMP contract expects. Kept out of the {@code room} package so
 * {@code PartyRoom}/{@code RoomRegistry} stay Spring- and DTO-free.
 */
public final class RoomDtoMapper {

    private RoomDtoMapper() {
    }

    public static ParticipantDTO toParticipantDto(Participant participant, String hostParticipantId) {
        return new ParticipantDTO(participant.id(), participant.name(), participant.id().equals(hostParticipantId));
    }

    public static PlaybackStateDTO toPlaybackStateDto(PlaybackState state) {
        return new PlaybackStateDTO(state.currentTrack(), state.isPlaying(), state.currentTimeSeconds(),
                state.serverTimestampMs());
    }

    public static ChatMessageDto toChatMessageDto(PartyRoom.ChatMessage message) {
        return new ChatMessageDto(message.senderParticipantId(), message.senderName(), message.text(),
                message.isEmoji(), message.timestamp());
    }

    public static List<ParticipantDTO> toParticipantDtos(PartyRoom.RoomSnapshot snapshot) {
        return snapshot.participants().stream()
                .map(p -> toParticipantDto(p, snapshot.hostParticipantId()))
                .toList();
    }

    public static RoomStateMessage toStateMessage(PartyRoom.RoomSnapshot snapshot) {
        return new RoomStateMessage(toParticipantDtos(snapshot), snapshot.hostParticipantId(),
                toPlaybackStateDto(snapshot.playback()));
    }

    public static RoomSnapshotDTO toSnapshotDto(PartyRoom.RoomSnapshot snapshot) {
        List<ChatMessageDto> chatDtos = snapshot.chatHistory().stream()
                .map(RoomDtoMapper::toChatMessageDto)
                .toList();
        return new RoomSnapshotDTO(
                toParticipantDtos(snapshot),
                snapshot.hostParticipantId(),
                toPlaybackStateDto(snapshot.playback()),
                snapshot.queue(),
                chatDtos
        );
    }
}

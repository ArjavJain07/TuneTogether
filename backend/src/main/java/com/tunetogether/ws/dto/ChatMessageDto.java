package com.tunetogether.ws.dto;

/** Broadcast on {@code /topic/room/{code}/chat} per new message. */
public record ChatMessageDto(String senderParticipantId, String senderName, String text, boolean isEmoji,
                              long timestamp) {}

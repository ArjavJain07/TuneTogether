package com.tunetogether.ws.dto;

/** Body of {@code /app/room/{code}/chat}. */
public record ChatRequest(String text, boolean isEmoji) {}

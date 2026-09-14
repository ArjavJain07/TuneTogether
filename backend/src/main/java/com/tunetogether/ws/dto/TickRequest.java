package com.tunetogether.ws.dto;

/** Body of {@code /app/room/{code}/playback/tick}. */
public record TickRequest(double positionSeconds) {}

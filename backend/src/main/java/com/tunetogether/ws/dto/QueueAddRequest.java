package com.tunetogether.ws.dto;

import com.tunetogether.common.TrackDto;

/** Body of {@code /app/room/{code}/queue/add}. */
public record QueueAddRequest(TrackDto track) {}

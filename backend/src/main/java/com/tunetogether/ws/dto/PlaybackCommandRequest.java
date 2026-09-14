package com.tunetogether.ws.dto;

import com.tunetogether.common.TrackDto;
import com.tunetogether.room.PartyRoom;

/** Body of {@code /app/room/{code}/playback}. {@code track}/{@code positionSeconds} are nullable. */
public record PlaybackCommandRequest(PartyRoom.PlaybackAction action, TrackDto track, Double positionSeconds) {}

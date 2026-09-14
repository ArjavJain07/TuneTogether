package com.tunetogether.ws.dto;

import com.tunetogether.common.TrackDto;

public record PlaybackStateDTO(TrackDto currentTrack, boolean isPlaying, double currentTimeSeconds,
                                long serverTimestampMs) {}

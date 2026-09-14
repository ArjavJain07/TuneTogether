package com.tunetogether.room;

import com.tunetogether.common.TrackDto;

/**
 * Immutable snapshot of a room's playback clock. Always mutated by swapping the whole
 * record reference (never individual fields) so a concurrent reader can never observe
 * a torn combination, e.g. a fresh {@code currentTimeSeconds} paired with a stale
 * {@code isPlaying}.
 */
public record PlaybackState(TrackDto currentTrack, boolean isPlaying, double currentTimeSeconds,
                             long serverTimestampMs) {

    public static PlaybackState empty() {
        return new PlaybackState(null, false, 0.0, System.currentTimeMillis());
    }
}

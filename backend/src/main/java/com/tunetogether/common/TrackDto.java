package com.tunetogether.common;

/**
 * Wire/storage shape for a single track. Mirrors the JSON object the original
 * frontend already produces from YouTube search results and its hardcoded demo
 * tracks: {id, title, artist, album, albumArt, duration, previewUrl, uri, youtubeId}.
 * Used as-is by the music search API, the library/playlist API, and the party
 * queue payloads, so the frontend needs no shape translation anywhere.
 */
public record TrackDto(
        String id,
        String title,
        String artist,
        String album,
        String albumArt,
        long duration,
        String previewUrl,
        String uri,
        String youtubeId
) {
}

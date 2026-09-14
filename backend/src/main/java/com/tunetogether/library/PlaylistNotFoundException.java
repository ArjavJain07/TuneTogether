package com.tunetogether.library;

/**
 * Thrown both when a playlist genuinely doesn't exist and when it exists but is
 * owned by someone else - deliberately the same exception/response (404, not
 * 403) so a caller can't distinguish "doesn't exist" from "not yours" and use
 * that to enumerate other users' playlist ids.
 */
public class PlaylistNotFoundException extends RuntimeException {

    public PlaylistNotFoundException(Long playlistId) {
        super("No playlist with id " + playlistId);
    }
}

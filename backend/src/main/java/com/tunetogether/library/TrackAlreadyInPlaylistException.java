package com.tunetogether.library;

/** Mirrors the original app's "Already in {name}" toast - now enforced server-side too. */
public class TrackAlreadyInPlaylistException extends RuntimeException {

    public TrackAlreadyInPlaylistException(String playlistName) {
        super("Already in " + playlistName);
    }
}

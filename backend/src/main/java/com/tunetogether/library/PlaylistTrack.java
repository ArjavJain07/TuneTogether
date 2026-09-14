package com.tunetogether.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Ordered join between a playlist and a (deduplicated) track. The unique
 * constraint on (playlist, track) is a DB-level backstop for the original's
 * client-side "already in playlist" check.
 */
@Entity
@Table(name = "playlist_track", uniqueConstraints = {
        @UniqueConstraint(name = "uk_playlist_track", columnNames = {"playlist_id", "track_id"})
})
public class PlaylistTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @Column(nullable = false)
    private int position;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected PlaylistTrack() {
        // JPA
    }

    public PlaylistTrack(Playlist playlist, Track track, int position) {
        this.playlist = playlist;
        this.track = track;
        this.position = position;
        this.addedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Track getTrack() {
        return track;
    }

    public int getPosition() {
        return position;
    }
}

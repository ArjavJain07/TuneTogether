package com.tunetogether.library;

import com.tunetogether.common.TrackDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * A deduplicated track. The original app stored a full copy of the track JSON
 * in every playlist that referenced it (fine for Firebase's document model,
 * wasteful in a relational one) - here, the same YouTube video liked and added
 * to three playlists is one row, referenced three times via PlaylistTrack.
 * <p>
 * Deduplicated by {@code naturalKey}, set to the client's existing {@code uri}
 * field, which is already globally unique across both YouTube results
 * ("youtube:video:<id>") and the frontend's hardcoded demo tracks ("demo:track:<n>").
 */
@Entity
@Table(name = "track", uniqueConstraints = {
        @UniqueConstraint(name = "uk_track_natural_key", columnNames = "natural_key")
})
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "natural_key", nullable = false, updatable = false)
    private String naturalKey;

    /**
     * The frontend's own {@code track.id} field (distinct from {@code uri}/naturalKey -
     * e.g. a bare YouTube video id like "dQw4w9WgXcQ", or "demo3" for a demo track).
     * The frontend's own "already liked"/"already in playlist" checks compare on this
     * field, so it must round-trip unchanged rather than being conflated with naturalKey.
     */
    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "youtube_id")
    private String youtubeId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String artist;

    private String album;

    @Column(name = "album_art")
    private String albumArt;

    @Column(name = "preview_url")
    private String previewUrl;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Track() {
        // JPA
    }

    public Track(TrackDto dto) {
        this.naturalKey = dto.uri();
        this.externalId = dto.id();
        this.youtubeId = dto.youtubeId();
        this.title = dto.title();
        this.artist = dto.artist();
        this.album = dto.album();
        this.albumArt = dto.albumArt();
        this.previewUrl = dto.previewUrl();
        this.durationMs = dto.duration();
        this.createdAt = Instant.now();
    }

    public TrackDto toDto() {
        return new TrackDto(externalId, title, artist, album, albumArt, durationMs, previewUrl, naturalKey, youtubeId);
    }

    public Long getId() {
        return id;
    }

    public String getNaturalKey() {
        return naturalKey;
    }
}

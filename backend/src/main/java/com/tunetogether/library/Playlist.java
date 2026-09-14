package com.tunetogether.library;

import com.tunetogether.auth.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * {@code liked} replaces the original app's fragile {@code name === 'Liked Songs'}
 * string match with an explicit flag - the "Liked Songs" playlist is get-or-created
 * per owner via PlaylistRepository.findByOwnerIdAndLikedTrue.
 */
@Entity
@Table(name = "playlist", indexes = {
        @Index(name = "idx_playlist_owner", columnList = "owner_id")
})
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean liked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Playlist() {
        // JPA
    }

    public Playlist(AppUser owner, String name, boolean liked) {
        this.owner = owner;
        this.name = name;
        this.liked = liked;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public boolean isLiked() {
        return liked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

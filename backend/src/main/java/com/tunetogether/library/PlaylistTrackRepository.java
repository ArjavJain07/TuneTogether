package com.tunetogether.library;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Ownership is enforced one level up: callers always resolve the {@link Playlist}
 * through PlaylistRepository's owner-scoped finders first, then operate on its
 * tracks by the already-verified playlist id - so an unscoped playlistId here
 * can't be used to bypass ownership.
 */
public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrack, Long> {

    List<PlaylistTrack> findAllByPlaylistIdOrderByPositionAsc(Long playlistId);

    boolean existsByPlaylistIdAndTrackId(Long playlistId, Long trackId);

    void deleteByPlaylistIdAndTrackId(Long playlistId, Long trackId);

    long countByPlaylistId(Long playlistId);
}

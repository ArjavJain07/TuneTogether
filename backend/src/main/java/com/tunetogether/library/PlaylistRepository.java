package com.tunetogether.library;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Deliberately extends the bare Spring Data {@link Repository} marker (NOT
 * {@code JpaRepository}) rather than exposing an unscoped {@code findById}/
 * {@code deleteById}. Every method here takes {@code ownerId}, sourced only from
 * the JWT-derived principal - never from a client-supplied id - so a caller can
 * never reach another user's playlist even with a crafted request. This mirrors
 * the original Firebase rule {@code "$uid === auth.uid"}, except enforced at
 * compile time: an unscoped lookup method simply isn't declared here, so it's not
 * a mistake a future change here can make by accident.
 */
public interface PlaylistRepository extends Repository<Playlist, Long> {

    Playlist save(Playlist playlist);

    Optional<Playlist> findByIdAndOwnerId(Long id, Long ownerId);

    List<Playlist> findAllByOwnerIdOrderByCreatedAtAsc(Long ownerId);

    Optional<Playlist> findByOwnerIdAndLikedTrue(Long ownerId);

    void deleteByIdAndOwnerId(Long id, Long ownerId);
}

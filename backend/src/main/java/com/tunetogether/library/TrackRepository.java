package com.tunetogether.library;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Global dedup table, not per-user, so an unscoped lookup by natural key is fine. */
public interface TrackRepository extends JpaRepository<Track, Long> {

    Optional<Track> findByNaturalKey(String naturalKey);
}

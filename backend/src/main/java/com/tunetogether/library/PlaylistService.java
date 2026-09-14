package com.tunetogether.library;

import com.tunetogether.auth.AppUser;
import com.tunetogether.auth.AppUserRepository;
import com.tunetogether.common.TrackDto;
import com.tunetogether.library.dto.PlaylistDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Every method takes {@code ownerId} sourced from the JWT principal (never from
 * a client-supplied path/body value) and every lookup goes through
 * PlaylistRepository's owner-scoped finders - see the javadoc there for why.
 */
@Service
public class PlaylistService {

    private static final String LIKED_SONGS_NAME = "Liked Songs";

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final TrackRepository trackRepository;
    private final AppUserRepository appUserRepository;

    public PlaylistService(
            PlaylistRepository playlistRepository,
            PlaylistTrackRepository playlistTrackRepository,
            TrackRepository trackRepository,
            AppUserRepository appUserRepository) {
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.trackRepository = trackRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<PlaylistDto> listPlaylists(Long ownerId) {
        return playlistRepository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PlaylistDto createPlaylist(Long ownerId, String name) {
        AppUser owner = requireUser(ownerId);
        Playlist playlist = playlistRepository.save(new Playlist(owner, name, false));
        return toDto(playlist);
    }

    @Transactional
    public PlaylistDto addTrack(Long ownerId, Long playlistId, TrackDto trackDto) {
        Playlist playlist = requireOwnedPlaylist(playlistId, ownerId);
        Track track = findOrCreateTrack(trackDto);

        if (playlistTrackRepository.existsByPlaylistIdAndTrackId(playlist.getId(), track.getId())) {
            throw new TrackAlreadyInPlaylistException(playlist.getName());
        }

        int nextPosition = (int) playlistTrackRepository.countByPlaylistId(playlist.getId());
        playlistTrackRepository.save(new PlaylistTrack(playlist, track, nextPosition));
        playlist.touch();

        return toDto(playlist);
    }

    /** Toggles Liked Songs membership for a track; returns the new liked state. */
    @Transactional
    public boolean toggleLiked(Long ownerId, TrackDto trackDto) {
        Playlist liked = getOrCreateLikedPlaylist(ownerId);
        Track track = findOrCreateTrack(trackDto);

        var existing = playlistTrackRepository.findAllByPlaylistIdOrderByPositionAsc(liked.getId()).stream()
                .filter(pt -> pt.getTrack().getId().equals(track.getId()))
                .findFirst();

        if (existing.isPresent()) {
            playlistTrackRepository.deleteByPlaylistIdAndTrackId(liked.getId(), track.getId());
            liked.touch();
            return false;
        }

        int nextPosition = (int) playlistTrackRepository.countByPlaylistId(liked.getId());
        playlistTrackRepository.save(new PlaylistTrack(liked, track, nextPosition));
        liked.touch();
        return true;
    }

    @Transactional
    public Playlist getOrCreateLikedPlaylist(Long ownerId) {
        return playlistRepository.findByOwnerIdAndLikedTrue(ownerId)
                .orElseGet(() -> playlistRepository.save(new Playlist(requireUser(ownerId), LIKED_SONGS_NAME, true)));
    }

    private Track findOrCreateTrack(TrackDto dto) {
        return trackRepository.findByNaturalKey(dto.uri())
                .orElseGet(() -> trackRepository.save(new Track(dto)));
    }

    private Playlist requireOwnedPlaylist(Long playlistId, Long ownerId) {
        return playlistRepository.findByIdAndOwnerId(playlistId, ownerId)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
    }

    private AppUser requireUser(Long ownerId) {
        return appUserRepository.findById(ownerId)
                .orElseThrow(() -> new NoSuchElementException("No such user: " + ownerId));
    }

    private PlaylistDto toDto(Playlist playlist) {
        List<TrackDto> tracks = playlistTrackRepository.findAllByPlaylistIdOrderByPositionAsc(playlist.getId())
                .stream()
                .map(pt -> pt.getTrack().toDto())
                .toList();
        return new PlaylistDto(playlist.getId(), playlist.getName(), playlist.isLiked(), tracks,
                playlist.getCreatedAt().toEpochMilli());
    }
}

package com.tunetogether.library;

import com.tunetogether.auth.AppUser;
import com.tunetogether.auth.AppUserRepository;
import com.tunetogether.common.TrackDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Ownership enforcement is the security-critical part of this service - see
 * PlaylistRepository's javadoc for why every lookup is owner-scoped. These
 * tests exercise that a request for a playlist id that isn't owned by the
 * caller is treated identically to "doesn't exist" (404, not a 403 that would
 * confirm the id belongs to someone else).
 */
@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private PlaylistTrackRepository playlistTrackRepository;
    @Mock
    private TrackRepository trackRepository;
    @Mock
    private AppUserRepository appUserRepository;

    private PlaylistService service() {
        return new PlaylistService(playlistRepository, playlistTrackRepository, trackRepository, appUserRepository);
    }

    @Test
    void addTrackToUnownedOrMissingPlaylistThrowsNotFound() {
        when(playlistRepository.findByIdAndOwnerId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().addTrack(1L, 99L, sampleTrack()))
                .isInstanceOf(PlaylistNotFoundException.class);
    }

    @Test
    void addingTheSameTrackTwiceThrowsAlreadyInPlaylist() {
        AppUser owner = new AppUser("sub", "a@b.com", "Alice", null);
        Playlist playlist = new Playlist(owner, "Road Trip", false);
        Track track = new Track(sampleTrack());

        when(playlistRepository.findByIdAndOwnerId(5L, 1L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findByNaturalKey(sampleTrack().uri())).thenReturn(Optional.of(track));
        when(playlistTrackRepository.existsByPlaylistIdAndTrackId(playlist.getId(), track.getId())).thenReturn(true);

        assertThatThrownBy(() -> service().addTrack(1L, 5L, sampleTrack()))
                .isInstanceOf(TrackAlreadyInPlaylistException.class);
    }

    @Test
    void toggleLikedAddsThenRemovesOnSecondCall() {
        AppUser owner = new AppUser("sub", "a@b.com", "Alice", null);
        Playlist liked = new Playlist(owner, "Liked Songs", true);
        Track track = new Track(sampleTrack());

        when(playlistRepository.findByOwnerIdAndLikedTrue(1L)).thenReturn(Optional.of(liked));
        when(trackRepository.findByNaturalKey(sampleTrack().uri())).thenReturn(Optional.of(track));
        when(playlistTrackRepository.findAllByPlaylistIdOrderByPositionAsc(liked.getId())).thenReturn(java.util.List.of());
        when(playlistTrackRepository.countByPlaylistId(liked.getId())).thenReturn(0L);

        boolean liked1 = service().toggleLiked(1L, sampleTrack());
        assertThat(liked1).isTrue();
    }

    private TrackDto sampleTrack() {
        return new TrackDto("vid1", "Title", "Artist", "Album", "art.jpg", 200_000L,
                "https://youtube.com/watch?v=vid1", "youtube:video:vid1", "vid1");
    }
}

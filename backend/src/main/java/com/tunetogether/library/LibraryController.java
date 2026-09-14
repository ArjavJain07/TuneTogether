package com.tunetogether.library;

import com.tunetogether.auth.TuneTogetherPrincipal;
import com.tunetogether.library.dto.AddTrackRequest;
import com.tunetogether.library.dto.CreatePlaylistRequest;
import com.tunetogether.library.dto.PlaylistDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Every endpoint requires ROLE_USER - guests are blocked entirely, matching the
 * original app's {@code if (!AUTH.user || AUTH.user.isGuest)} checks, except
 * this can't be bypassed by a crafted request the way a client-side check can.
 */
@RestController
@RequestMapping("/api/library")
@PreAuthorize("hasRole('USER')")
public class LibraryController {

    private final PlaylistService playlistService;

    public LibraryController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping("/playlists")
    public List<PlaylistDto> listPlaylists(Authentication authentication) {
        return playlistService.listPlaylists(ownerId(authentication));
    }

    @PostMapping("/playlists")
    public ResponseEntity<PlaylistDto> createPlaylist(
            Authentication authentication, @Valid @RequestBody CreatePlaylistRequest request) {
        PlaylistDto created = playlistService.createPlaylist(ownerId(authentication), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/playlists/{id}/tracks")
    public PlaylistDto addTrack(
            Authentication authentication, @PathVariable Long id, @Valid @RequestBody AddTrackRequest request) {
        return playlistService.addTrack(ownerId(authentication), id, request.track());
    }

    @PostMapping("/liked/tracks")
    public Map<String, Boolean> toggleLiked(Authentication authentication, @Valid @RequestBody AddTrackRequest request) {
        boolean liked = playlistService.toggleLiked(ownerId(authentication), request.track());
        return Map.of("liked", liked);
    }

    private Long ownerId(Authentication authentication) {
        return ((TuneTogetherPrincipal) authentication.getPrincipal()).appUserId();
    }
}

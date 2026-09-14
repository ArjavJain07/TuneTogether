package com.tunetogether.music;

import com.tunetogether.auth.TuneTogetherPrincipal;
import com.tunetogether.common.TrackDto;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Single consolidated search endpoint: does both YouTube API hops (search +
 * details) and all cleanup server-side, returning the exact track shape the
 * frontend already renders. The frontend's own catch-block/empty-results
 * fallback to demo tracks is unchanged and untouched by this endpoint.
 */
@RestController
@RequestMapping("/api/music")
public class MusicController {

    private final MusicSearchService musicSearchService;
    private final MusicRateLimiter rateLimiter;

    public MusicController(MusicSearchService musicSearchService, MusicRateLimiter rateLimiter) {
        this.musicSearchService = musicSearchService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/search")
    public List<TrackDto> search(Authentication authentication, @RequestParam String query) {
        String principalId = ((TuneTogetherPrincipal) authentication.getPrincipal()).subjectId();
        rateLimiter.checkAndRecord(principalId);
        return musicSearchService.search(query);
    }
}

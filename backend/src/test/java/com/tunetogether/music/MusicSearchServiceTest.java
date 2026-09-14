package com.tunetogether.music;

import com.tunetogether.common.TrackDto;
import com.tunetogether.music.dto.YouTubeVideoDetailsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies the Java port of app.js's cleanTitle/artist regexes and
 * parseYouTubeDuration() produces byte-for-byte the same output as the
 * original JS on the same input.
 */
@ExtendWith(MockitoExtension.class)
class MusicSearchServiceTest {

    @Mock
    private YouTubeClient youTubeClient;

    @Test
    void cleansOfficialVideoSuffixesFromTitleAndVevoFromArtist() {
        var response = detailsResponse(
                "vid1",
                "Never Gonna Give You Up (Official Video)",
                "RickAstleyVEVO",
                "PT3M33S");
        when(youTubeClient.search("rick astley official audio")).thenReturn(List.of("vid1"));
        when(youTubeClient.details(List.of("vid1"))).thenReturn(response);

        MusicSearchService svc = new MusicSearchService(youTubeClient);
        List<TrackDto> results = svc.search("rick astley");

        assertThat(results).hasSize(1);
        TrackDto track = results.get(0);
        assertThat(track.title()).isEqualTo("Never Gonna Give You Up");
        assertThat(track.artist()).isEqualTo("RickAstley");
        assertThat(track.duration()).isEqualTo(213_000L);
        assertThat(track.uri()).isEqualTo("youtube:video:vid1");
        assertThat(track.id()).isEqualTo("vid1");
    }

    @Test
    void fallsBackToRawTitleWhenCleanupLeavesItBlank() {
        var response = detailsResponse("vid2", "(Official Video)", "SomeChannel", "PT2M0S");
        when(youTubeClient.search("x official audio")).thenReturn(List.of("vid2"));
        when(youTubeClient.details(List.of("vid2"))).thenReturn(response);

        MusicSearchService svc = new MusicSearchService(youTubeClient);
        List<TrackDto> results = svc.search("x");

        assertThat(results.get(0).title()).isEqualTo("(Official Video)");
    }

    @Test
    void unparsableDurationDefaultsToThreeMinutes() {
        var response = detailsResponse("vid3", "Some Song", "Some Artist", "not-a-duration");
        when(youTubeClient.search("y official audio")).thenReturn(List.of("vid3"));
        when(youTubeClient.details(List.of("vid3"))).thenReturn(response);

        MusicSearchService svc = new MusicSearchService(youTubeClient);
        List<TrackDto> results = svc.search("y");

        assertThat(results.get(0).duration()).isEqualTo(180_000L);
    }

    @Test
    void queryAlreadyMentioningMusicIsNotExpanded() {
        when(youTubeClient.search("some song")).thenReturn(List.of());

        MusicSearchService svc = new MusicSearchService(youTubeClient);
        svc.search("some song");

        org.mockito.Mockito.verify(youTubeClient).search("some song");
    }

    private YouTubeVideoDetailsResponse detailsResponse(String id, String title, String channelTitle, String duration) {
        var thumbnails = new YouTubeVideoDetailsResponse.Thumbnails(
                new YouTubeVideoDetailsResponse.Thumbnail("https://example.com/hi.jpg"), null, null);
        var snippet = new YouTubeVideoDetailsResponse.Snippet(title, channelTitle, thumbnails);
        var contentDetails = new YouTubeVideoDetailsResponse.ContentDetails(duration);
        var item = new YouTubeVideoDetailsResponse.Item(id, snippet, contentDetails);
        return new YouTubeVideoDetailsResponse(List.of(item));
    }
}

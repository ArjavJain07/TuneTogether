package com.tunetogether.music;

import com.tunetogether.common.TrackDto;
import com.tunetogether.config.CacheConfig;
import com.tunetogether.music.dto.YouTubeVideoDetailsResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reimplements app.js's searchYouTube()/parseYouTubeDuration() server-side, byte
 * -for-byte equivalent on the same input, so moving this logic off the client
 * doesn't change what the frontend renders.
 */
@Service
public class MusicSearchService {

    // Matches the original's unanchored JS `.match()` - Matcher#find(), not #matches().
    private static final Pattern ISO_8601_DURATION = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");
    private static final long DEFAULT_DURATION_MS = 180_000L;

    private final YouTubeClient youTubeClient;

    public MusicSearchService(YouTubeClient youTubeClient) {
        this.youTubeClient = youTubeClient;
    }

    /**
     * Cache key is the normalized (trimmed, lower-cased) query the caller passed in -
     * not the internally-mutated "... official audio" variant - so case-only
     * variants of the same search share one cache entry.
     */
    @Cacheable(cacheNames = CacheConfig.YOUTUBE_SEARCH_CACHE, key = "#query.trim().toLowerCase()")
    public List<TrackDto> search(String query) {
        String musicQuery = expandQuery(query);

        List<String> videoIds = youTubeClient.search(musicQuery);
        if (videoIds.isEmpty()) {
            return List.of();
        }

        YouTubeVideoDetailsResponse details = youTubeClient.details(videoIds);
        if (details.items() == null) {
            return List.of();
        }

        return details.items().stream().map(this::toTrackDto).toList();
    }

    private String expandQuery(String query) {
        String lower = query.toLowerCase();
        boolean alreadyMusicy = lower.contains("song") || lower.contains("music") || lower.contains("audio");
        return alreadyMusicy ? query : query + " official audio";
    }

    private TrackDto toTrackDto(YouTubeVideoDetailsResponse.Item item) {
        String videoId = item.id();
        YouTubeVideoDetailsResponse.Snippet snippet = item.snippet();
        long durationMs = item.contentDetails() == null
                ? DEFAULT_DURATION_MS
                : parseDuration(item.contentDetails().duration());

        String rawTitle = snippet == null ? "" : snippet.title();
        String cleanTitle = cleanTitle(rawTitle);
        String title = cleanTitle.isBlank() ? rawTitle : cleanTitle;

        String rawChannel = snippet == null ? "" : snippet.channelTitle();
        String cleanArtist = cleanArtist(rawChannel);
        String artist = cleanArtist.isBlank() ? rawChannel : cleanArtist;

        String albumArt = pickThumbnail(snippet);

        return new TrackDto(
                videoId,
                title,
                artist,
                "YouTube Music",
                albumArt,
                durationMs,
                "https://www.youtube.com/watch?v=" + videoId,
                "youtube:video:" + videoId,
                videoId);
    }

    /** ISO-8601 duration (e.g. PT4M13S) -> milliseconds. Defaults to 3 minutes if unparsable. */
    private long parseDuration(String iso8601Duration) {
        if (iso8601Duration == null) {
            return DEFAULT_DURATION_MS;
        }
        Matcher matcher = ISO_8601_DURATION.matcher(iso8601Duration);
        if (!matcher.find()) {
            return DEFAULT_DURATION_MS;
        }
        long hours = parseGroupOrZero(matcher, 1);
        long minutes = parseGroupOrZero(matcher, 2);
        long seconds = parseGroupOrZero(matcher, 3);
        return (hours * 3600 + minutes * 60 + seconds) * 1000;
    }

    private long parseGroupOrZero(Matcher matcher, int group) {
        String value = matcher.group(group);
        return value == null ? 0 : Long.parseLong(value);
    }

    private String cleanTitle(String title) {
        if (title == null) {
            return "";
        }
        return title
                .replaceAll("(?i)\\(Official .*?\\)", "")
                .replaceAll("(?i)\\[Official .*?\\]", "")
                .replaceAll("(?i)- Official .*", "")
                .replaceAll("(?i)\\(.*?Video\\)", "")
                .replaceAll("(?i)\\[.*?Video\\]", "")
                .trim();
    }

    private String cleanArtist(String channelTitle) {
        if (channelTitle == null) {
            return "";
        }
        return channelTitle
                .replaceAll("(?i)VEVO$", "")
                .replaceAll("(?i)Official$", "")
                .replaceAll("(?i)Music$", "")
                .trim();
    }

    private String pickThumbnail(YouTubeVideoDetailsResponse.Snippet snippet) {
        if (snippet == null || snippet.thumbnails() == null) {
            return null;
        }
        YouTubeVideoDetailsResponse.Thumbnails thumbnails = snippet.thumbnails();
        if (thumbnails.high() != null) {
            return thumbnails.high().url();
        }
        if (thumbnails.medium() != null) {
            return thumbnails.medium().url();
        }
        return thumbnails.defaultThumbnail() != null ? thumbnails.defaultThumbnail().url() : null;
    }
}

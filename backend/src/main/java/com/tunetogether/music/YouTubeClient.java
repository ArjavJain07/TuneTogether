package com.tunetogether.music;

import com.tunetogether.common.UpstreamServiceException;
import com.tunetogether.music.dto.YouTubeSearchResponse;
import com.tunetogether.music.dto.YouTubeVideoDetailsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Talks to the YouTube Data API v3. The original MusicController string-
 * concatenated the client-supplied query straight into the outbound URL
 * ("...&q=" + musicQuery + "...&key=" + apiKey), which let a crafted query
 * inject extra query parameters into the upstream request (parameter
 * smuggling - e.g. overriding {@code key}/{@code maxResults}) or malform the
 * URI outright. Every URL here is instead built with UriComponentsBuilder's
 * templated placeholders + encode(), which encodes the *value*, never a
 * pre-concatenated string.
 */
@Component
public class YouTubeClient {

    private static final Pattern VALID_VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final int MAX_RESULTS = 20;
    private static final int MAX_VIDEO_IDS_PER_DETAILS_CALL = 50; // YouTube API's own limit

    private final RestClient restClient;
    private final String apiKey;

    public YouTubeClient(RestClient restClient, @Value("${app.youtube.api-key}") String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Returns up to {@value MAX_RESULTS} video ids matching the query. */
    public List<String> search(String query) {
        requireConfigured();

        URI uri = buildSearchUri(query);
        YouTubeSearchResponse response = get(uri, YouTubeSearchResponse.class, "YouTube search");
        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream()
                .map(YouTubeSearchResponse.Item::id)
                .filter(id -> id != null && id.videoId() != null)
                .map(YouTubeSearchResponse.Id::videoId)
                .toList();
    }

    public YouTubeVideoDetailsResponse details(List<String> videoIds) {
        requireConfigured();

        List<String> validIds = videoIds.stream()
                .filter(id -> VALID_VIDEO_ID.matcher(id).matches())
                .limit(MAX_VIDEO_IDS_PER_DETAILS_CALL)
                .toList();
        if (validIds.isEmpty()) {
            return new YouTubeVideoDetailsResponse(List.of());
        }

        URI uri = buildDetailsUri(validIds);
        YouTubeVideoDetailsResponse response = get(uri, YouTubeVideoDetailsResponse.class, "YouTube video details");
        return response == null ? new YouTubeVideoDetailsResponse(List.of()) : response;
    }

    /**
     * Package-private so tests can assert the injection fix directly on the built
     * URI without needing a live/mocked HTTP call: a query containing "&amp;key=..."
     * must land entirely inside the encoded "q" value, never as a sibling parameter.
     */
    URI buildSearchUri(String query) {
        return UriComponentsBuilder.fromUriString("https://www.googleapis.com/youtube/v3/search")
                .queryParam("part", "snippet")
                .queryParam("q", "{q}")
                .queryParam("type", "video")
                .queryParam("videoCategoryId", "10")
                .queryParam("maxResults", MAX_RESULTS)
                .queryParam("order", "relevance")
                .queryParam("key", "{key}")
                .encode()
                .buildAndExpand(query, apiKey)
                .toUri();
    }

    URI buildDetailsUri(List<String> validVideoIds) {
        return UriComponentsBuilder.fromUriString("https://www.googleapis.com/youtube/v3/videos")
                .queryParam("part", "contentDetails,snippet")
                .queryParam("id", "{ids}")
                .queryParam("key", "{key}")
                .encode()
                .buildAndExpand(String.join(",", validVideoIds), apiKey)
                .toUri();
    }

    private <T> T get(URI uri, Class<T> type, String what) {
        try {
            return restClient.get().uri(uri).retrieve().body(type);
        } catch (RestClientException e) {
            throw new UpstreamServiceException(what + " request failed", e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new UpstreamServiceException("YouTube API is not configured on this server");
        }
    }
}

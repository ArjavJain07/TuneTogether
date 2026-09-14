package com.tunetogether.music;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the fix for the original MusicController's URL-injection bug: a raw
 * string-concatenated query like {@code "...&q=" + musicQuery + "...&key=" + apiKey}
 * let a crafted {@code musicQuery} smuggle extra query parameters (overriding
 * {@code key}/{@code maxResults}) into the outbound YouTube request. Every URL
 * here is built via UriComponentsBuilder's templated placeholders + encode(),
 * which encodes the *value*, so attacker-controlled content can never become a
 * sibling parameter.
 */
class YouTubeClientTest {

    private static final String REAL_API_KEY = "REAL_SERVER_KEY";

    private final YouTubeClient client = new YouTubeClient(null, REAL_API_KEY);

    @Test
    void maliciousQueryCannotInjectAdditionalParameters() {
        String maliciousQuery = "foo&key=ATTACKER_KEY&maxResults=1&videoCategoryId=0";

        URI uri = client.buildSearchUri(maliciousQuery);
        Map<String, String> queryParams = decodedQueryParams(uri);

        // The real key must appear exactly once, with its real value - never overridden.
        assertThat(queryParams.get("key")).isEqualTo(REAL_API_KEY);
        assertThat(queryParams.get("maxResults")).isEqualTo("20");
        assertThat(queryParams.get("videoCategoryId")).isEqualTo("10");

        // "q" is the only place the malicious content is allowed to end up.
        assertThat(queryParams.get("q")).contains("ATTACKER_KEY");
    }

    @Test
    void queryWithHashAndPercentDoesNotProduceAMalformedUri() {
        URI uri = client.buildSearchUri("50% off #1 hit song");
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("www.googleapis.com");
    }

    @Test
    void detailsUriJoinsValidVideoIdsAndKeepsRealKey() {
        URI uri = client.buildDetailsUri(List.of("dQw4w9WgXcQ", "abcDEF12345"));
        Map<String, String> queryParams = decodedQueryParams(uri);
        assertThat(queryParams.get("id")).isEqualTo("dQw4w9WgXcQ,abcDEF12345");
        assertThat(queryParams.get("key")).isEqualTo(REAL_API_KEY);
    }

    /**
     * Splits on the RAW (still percent-encoded) query first - a literal "&" or "="
     * that was part of an attacker-controlled value is encoded as %26/%3D at this
     * point, so it can never masquerade as a delimiter here. Only then is each
     * individual value decoded. Decoding the whole query string before splitting
     * would undo that protection and re-introduce exactly the ambiguity this test
     * is supposed to catch.
     */
    private Map<String, String> decodedQueryParams(URI uri) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }
}

package com.tunetogether.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Verifies a Google Identity Services credential (ID token) server-side using
 * Google's tokeninfo endpoint, which performs the signature/expiry/issuer checks
 * for us and returns the decoded claims. We additionally check the audience
 * ourselves against our configured OAuth client ID, since tokeninfo doesn't know
 * which client we expect the token to be for.
 * <p>
 * (For very high request volumes, verifying locally against Google's published
 * JWKS avoids the extra network round trip and tokeninfo's rate limit - not a
 * concern at this app's scale, and this keeps the dependency footprint small.)
 */
@Service
public class GoogleIdTokenService {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final RestClient restClient;
    private final String expectedAudience;

    public GoogleIdTokenService(RestClient restClient, @Value("${app.google.client-id}") String expectedAudience) {
        this.restClient = restClient;
        this.expectedAudience = expectedAudience;
    }

    public GoogleUserInfo verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidTokenException("Missing Google ID token");
        }
        if (expectedAudience == null || expectedAudience.isBlank()) {
            throw new InvalidTokenException("Google Sign-In is not configured on this server");
        }

        TokenInfoResponse response;
        try {
            response = restClient.get()
                    .uri(TOKENINFO_URL + "?id_token={token}", idToken)
                    .retrieve()
                    .body(TokenInfoResponse.class);
        } catch (RestClientException e) {
            throw new InvalidTokenException("Google ID token rejected", e);
        }

        if (response == null || response.sub == null) {
            throw new InvalidTokenException("Google ID token rejected");
        }
        if (!expectedAudience.equals(response.aud)) {
            throw new InvalidTokenException("Google ID token was issued for a different client");
        }

        return new GoogleUserInfo(response.sub, response.email, response.name, response.picture);
    }

    public record GoogleUserInfo(String sub, String email, String name, String picture) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class TokenInfoResponse {
        public String sub;
        public String aud;
        public String email;
        public String name;
        public String picture;
    }
}

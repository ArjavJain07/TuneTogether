/**
 * config.js - Frontend configuration for the Java-backed TuneTogether.
 * All API keys and secrets now live server-side only (backend/src/main/resources/
 * application.properties, via env vars) - nothing sensitive belongs in this file
 * anymore, which is also why it's safe to commit.
 */

const CONFIG = {
    // The Java backend (Spring Boot). REST + WebSocket/STOMP both live here.
    BACKEND_URL: 'http://localhost:8081',
    WS_URL: 'http://localhost:8081/ws',

    // Google Identity Services client ID (for the Google Sign-In button).
    // Get one from https://console.cloud.google.com/apis/credentials -
    // create an OAuth 2.0 "Web application" client and add this page's
    // origin under "Authorized JavaScript origins". Leave blank to disable
    // Google Sign-In - Guest Mode keeps working regardless.
    GOOGLE_CLIENT_ID: '',

    MAX_SEARCH_RESULTS: 20,
    ROOM_CODE_LENGTH: 6,

    // If the backend's YouTube search isn't configured (or is unreachable),
    // the app falls back to a fixed set of demo tracks so playback and party
    // mode can still be exercised end-to-end without any API keys.
    DEMO_MODE: false
};

// Export for use in other files
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CONFIG;
}

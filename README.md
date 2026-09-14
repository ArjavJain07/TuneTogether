# TuneTogether (Java backend)

A Java rewrite of [TuneTogether](https://github.com/ArjavJain07/TuneTogether)'s server side. The original was a client-side web app that used Firebase (Auth + Realtime Database) for login and real-time "party mode" sync, and a tiny Spring Boot skeleton just to proxy YouTube search. This rewrite replaces Firebase entirely with a Spring Boot backend and keeps the original HTML/CSS/JS frontend, adapted to talk to it.

## What changed and why

- **Real-time party sync** moved from Firebase Realtime Database (+ a `localStorage`-polling fallback) to a genuinely concurrent, in-process room engine pushed to clients over WebSocket/STOMP. This is the part that actually needed "thread safety": many rooms, many participants per room, concurrent joins/leaves/queue-pushes/chat, host disconnect/reassignment, and idle-room cleanup, all without locking the whole system down. See [`backend/src/main/java/com/tunetogether/room`](backend/src/main/java/com/tunetogether/room) - one single-writer "actor" per room (`SerialExecutor`, backed by virtual threads) rather than a global lock or ad-hoc synchronization.
- **Auth** moved from Firebase Auth to Google Identity Services (client-side) + a Java backend that verifies the ID token and issues its own JWT access token + rotating refresh token. Guests still work with zero setup, same as before, but now get a real (if account-less) server-verified session.
- **Library/playlists** moved from Firebase RTDB JSON blobs to a real relational schema (`backend/src/main/java/com/tunetogether/library`), with tracks deduplicated instead of copied into every playlist that references them.
- **Music search** was consolidated into one backend endpoint that does both YouTube API calls and all cleanup server-side, fixed a URL-injection bug in the original's string-concatenated request URLs, and added caching + rate limiting.
- The frontend's demo-mode fallback (25 hardcoded tracks) is untouched and still works with **no backend configuration at all** - useful for trying the app immediately.

See the design rationale in more depth in code comments throughout `backend/src/main/java/com/tunetogether/room` and `.../auth`.

## Project layout

```
backend/    Spring Boot 4 / Java 21 backend (REST + WebSocket/STOMP + JPA)
frontend/   The original static frontend, adapted to call the Java backend
```

## Running it locally

You need a JDK 21+ (the backend's Maven wrapper handles Maven itself) and Python or Node for serving the static frontend.

**1. Start the backend** (from `backend/`):

```bash
./mvnw spring-boot:run
```

This starts on `http://localhost:8081` with an embedded H2 database (file-based, under `backend/data/`) - no external database needed for local development. Party mode, guest login, and demo-track search/playback all work with zero configuration.

**2. Serve the frontend** (from `frontend/`, in a second terminal):

```bash
python -m http.server 8080
```

Then open `http://localhost:8080`.

**3. (Optional) Enable Google Sign-In and real YouTube search:**

- Google Sign-In: create an OAuth 2.0 "Web application" client at the [Google Cloud Console](https://console.cloud.google.com/apis/credentials), add `http://localhost:8080` as an authorized JavaScript origin, then set `GOOGLE_CLIENT_ID` in `frontend/config.js` *and* as an environment variable for the backend before starting it:
  ```bash
  export GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
  ```
- YouTube search: get an API key from the [Google Cloud Console](https://console.cloud.google.com/) (enable "YouTube Data API v3"), then:
  ```bash
  export YOUTUBE_API_KEY=your-api-key
  ```
  Without this, search falls back to the frontend's demo tracks automatically - the app is fully usable either way.

## Testing

```bash
cd backend
./mvnw test
```

Notable test coverage:
- `room/` - concurrency stress tests proving thread-safety under real concurrent load (many simultaneous joins, host-disconnect races, queue-push races, room-code collisions, stale-disconnect-vs-reconnect fencing, and a deterministic reap-vs-join race) rather than just single-threaded happy-path tests.
- `music/` - proves the URL-injection fix (a malicious search query can no longer smuggle extra parameters into the outbound YouTube API request) and that the Java title/artist-cleanup and duration-parsing logic matches the original JS byte-for-byte on the same input.
- `library/` - ownership enforcement (a playlist id that isn't yours behaves identically to one that doesn't exist).

## Deploying

- **Backend**: any host that runs a Spring Boot jar. Set `SPRING_PROFILES_ACTIVE=prod`, `DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD` for PostgreSQL, `JWT_SECRET` (a long random string), and optionally `GOOGLE_CLIENT_ID`/`YOUTUBE_API_KEY`.
- **Frontend**: still just static files - GitHub Pages, Netlify, or any static host works, same as the original. Point `BACKEND_URL`/`WS_URL` in `frontend/config.js` at your deployed backend.

## Known gaps / deliberate scope decisions

- The original's unused, never-wired-up `dataconnect/schema.gql` (Artist/Album/Review/ListeningLog) was deliberately not carried over - it had no UI consumer in the original app and YouTube search doesn't provide the kind of stable artist/album identity that schema would need. `library/Track` deduplicates tracks by their existing `uri` field instead.
- The original repo had real-looking API keys/secrets committed in `config.js` (Spotify, YouTube, Firebase). None of that carries over - all secrets are environment variables on the backend now - but if those old keys are still live, they're worth rotating independently of this rewrite.

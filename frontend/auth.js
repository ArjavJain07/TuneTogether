/**
 * TuneTogether - Auth Module
 * Google Sign-In now goes through Google Identity Services (GIS) directly
 * (not the Firebase Auth SDK) - the ID token it returns is verified by our own
 * Java backend, which issues the app's own access/refresh tokens. Guest mode
 * also now round-trips through the backend (POST /api/auth/guest) so a guest
 * gets a real, verifiable session for the WebSocket layer, instead of the
 * original's purely-client-generated "Guest_1234" name.
 */
const AUTH = {
    user: null,
    accessToken: null,
    refreshToken: null,

    init() {
        document.getElementById('google-login-btn').addEventListener('click', () => this.googleLogin());
        document.getElementById('guest-login-btn').addEventListener('click', () => this.guestLogin());
        document.getElementById('logout-btn').addEventListener('click', () => this.logout());

        this.initGoogleIfReady();
        this.restoreSession();
    },

    // Called directly if GIS already loaded by the time init() runs, and also
    // wired below as window.onGoogleLibraryLoad in case it loads afterward
    // (GIS's script tag is async/defer, so load order isn't guaranteed) -
    // mirrors the existing window.onYouTubeIframeAPIReady pattern in app.js.
    initGoogleIfReady() {
        if (typeof google === 'undefined' || !google.accounts || !CONFIG.GOOGLE_CLIENT_ID) {
            return;
        }
        google.accounts.id.initialize({
            client_id: CONFIG.GOOGLE_CLIENT_ID,
            callback: (response) => this.handleGoogleCredential(response)
        });
        this._googleReady = true;
    },

    restoreSession() {
        const accessToken = localStorage.getItem('tt_access_token');
        const userJson = localStorage.getItem('tt_user');
        if (!accessToken || !userJson) {
            this.showLogin();
            return;
        }
        try {
            this.accessToken = accessToken;
            this.refreshToken = localStorage.getItem('tt_refresh_token');
            this.applyLoggedInUser(JSON.parse(userJson), /* showWelcomeToast */ false);
        } catch (e) {
            this.clearStoredSession();
            this.showLogin();
        }
    },

    googleLogin() {
        if (!CONFIG.GOOGLE_CLIENT_ID) {
            this.showToast('Google Sign-In is not configured. Using guest mode.', 'error');
            this.guestLogin();
            return;
        }
        if (!this._googleReady) {
            this.initGoogleIfReady();
        }
        if (typeof google === 'undefined' || !google.accounts) {
            this.showToast('Google Sign-In failed to load. Using guest mode.', 'error');
            this.guestLogin();
            return;
        }
        google.accounts.id.prompt((notification) => {
            if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
                this.showToast('Google Sign-In was dismissed.', 'error');
            }
        });
    },

    async handleGoogleCredential(response) {
        try {
            const authResponse = await this.postJson('/api/auth/google', { idToken: response.credential });
            this.applyAuthResponse(authResponse, true);
        } catch (e) {
            console.error('Google login error:', e);
            this.showToast('Google login failed: ' + e.message, 'error');
        }
    },

    async guestLogin() {
        try {
            const authResponse = await this.postJson('/api/auth/guest', {});
            this.applyAuthResponse(authResponse, true);
        } catch (e) {
            console.error('Guest login error:', e);
            this.showToast('Could not start guest session: ' + e.message, 'error');
        }
    },

    applyAuthResponse(authResponse, showWelcomeToast) {
        this.accessToken = authResponse.accessToken;
        this.refreshToken = authResponse.refreshToken || null;
        localStorage.setItem('tt_access_token', this.accessToken);
        if (this.refreshToken) {
            localStorage.setItem('tt_refresh_token', this.refreshToken);
        } else {
            localStorage.removeItem('tt_refresh_token');
        }
        localStorage.setItem('tt_user', JSON.stringify(authResponse.user));
        this.applyLoggedInUser(authResponse.user, showWelcomeToast);
    },

    applyLoggedInUser(user, showWelcomeToast) {
        this.user = { name: user.name, email: user.email, photo: user.photo, uid: user.uid, isGuest: user.isGuest };
        document.getElementById('login-screen').style.display = 'none';
        document.getElementById('app-wrapper').style.display = 'flex';
        document.getElementById('user-name').textContent = this.user.name;
        const avatar = document.getElementById('user-avatar');
        if (this.user.photo) {
            avatar.src = this.user.photo;
            avatar.style.display = 'block';
        } else {
            avatar.src = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect fill="%236c5ce7" width="40" height="40" rx="20"/><text x="50%" y="55%" text-anchor="middle" fill="white" font-size="16" dominant-baseline="middle">' + (this.user.name.charAt(0).toUpperCase()) + '</text></svg>';
            avatar.style.display = 'block';
        }
        if (showWelcomeToast) {
            this.showToast('Welcome, ' + this.user.name + '!', 'success');
        }
        if (typeof initializeApp === 'function') initializeApp();
    },

    async logout() {
        if (this.refreshToken) {
            try {
                await this.postJson('/api/auth/logout', { refreshToken: this.refreshToken });
            } catch (e) {
                // Best-effort - proceed with local logout regardless.
            }
        }
        this.clearStoredSession();
        this.user = null;
        this.accessToken = null;
        this.refreshToken = null;
        document.getElementById('login-screen').style.display = 'flex';
        document.getElementById('app-wrapper').style.display = 'none';
    },

    clearStoredSession() {
        localStorage.removeItem('tt_access_token');
        localStorage.removeItem('tt_refresh_token');
        localStorage.removeItem('tt_user');
    },

    showLogin() {
        document.getElementById('login-screen').style.display = 'flex';
        document.getElementById('app-wrapper').style.display = 'none';
    },

    showToast(msg, type) {
        const c = document.getElementById('toast-container');
        if (!c) return;
        const t = document.createElement('div');
        t.className = 'toast ' + (type || '');
        t.textContent = msg;
        c.appendChild(t);
        setTimeout(() => { t.style.opacity = '0'; setTimeout(() => t.remove(), 300); }, 3000);
    },

    async postJson(path, body) {
        const response = await fetch(CONFIG.BACKEND_URL + path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!response.ok) {
            const errorBody = await response.json().catch(() => ({}));
            throw new Error(errorBody.message || ('Request failed: ' + response.status));
        }
        return response.status === 204 ? null : response.json();
    },

    /**
     * Attaches the bearer token to every backend call the rest of the app makes
     * (music search, library/playlists). Retries once through /api/auth/refresh
     * on a 401 before giving up - transparent to the caller.
     */
    async authFetch(path, options = {}) {
        const doFetch = () => fetch(CONFIG.BACKEND_URL + path, {
            ...options,
            headers: {
                ...(options.headers || {}),
                'Authorization': 'Bearer ' + this.accessToken
            }
        });

        let response = await doFetch();
        if (response.status === 401 && this.refreshToken) {
            const refreshed = await this.tryRefresh();
            if (refreshed) {
                response = await doFetch();
            }
        }
        return response;
    },

    async tryRefresh() {
        try {
            const result = await this.postJson('/api/auth/refresh', { refreshToken: this.refreshToken });
            this.accessToken = result.accessToken;
            this.refreshToken = result.refreshToken;
            localStorage.setItem('tt_access_token', this.accessToken);
            localStorage.setItem('tt_refresh_token', this.refreshToken);
            return true;
        } catch (e) {
            this.logout();
            return false;
        }
    }
};

// GIS's script tag loads async/defer; if it finishes after our init() already
// ran, this callback lets us initialize it late instead of missing the window.
window.onGoogleLibraryLoad = function() {
    AUTH.initGoogleIfReady();
};

document.addEventListener('DOMContentLoaded', () => AUTH.init());

/**
 * Service Worker for TuneTogether - offline support and faster loading.
 *
 * The original version cached everything cache-first with a CACHE_NAME that
 * never changed, so once a page was cached, every subsequent deploy (including
 * this Java-backend rewrite) kept getting served the *first-ever* cached copy
 * indefinitely - no code change, including this file's own edits, ever reached
 * a returning browser without a manual "clear site data". Navigation requests
 * (the HTML page itself) now go network-first with a cache fallback for
 * offline use; static assets stay cache-first since they're already
 * cache-busted via the ?v=N query string in index.html's script tags.
 */
const CACHE_NAME = 'tunetogether-v2';
const urlsToCache = [
    '/',
    '/index.html',
    '/styles.css',
    '/app.js',
    '/config.js',
    '/imgs/logo.png',
    '/imgs/banner.png',
    'https://fonts.googleapis.com/css2?family=Poppins:wght@300;400;600;700&display=swap'
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then((cache) => cache.addAll(urlsToCache))
            .then(() => self.skipWaiting())
    );
});

self.addEventListener('fetch', (event) => {
    if (event.request.mode === 'navigate') {
        event.respondWith(
            fetch(event.request).catch(() => caches.match(event.request))
        );
        return;
    }

    event.respondWith(
        caches.match(event.request).then((response) => response || fetch(event.request))
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        Promise.all([
            caches.keys().then((cacheNames) =>
                Promise.all(
                    cacheNames
                        .filter((cacheName) => cacheName !== CACHE_NAME)
                        .map((cacheName) => caches.delete(cacheName))
                )
            ),
            self.clients.claim()
        ])
    );
});

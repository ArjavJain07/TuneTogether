/**
 * TuneTogether - Main Application JavaScript
 * Handles music search, playback, party mode, and playlists
 * Made by OGARSH
 */

// ==================== STATE MANAGEMENT ====================
let state = {
    currentTrack: null,
    isPlaying: false,
    queue: [],
    partyQueue: [],
    searchResults: [],
    currentIndex: 0,
    playlists: [],
    volume: 70,
    partyRoom: null,
    isHost: false,
    participants: [],
    participantId: null,
    stompClient: null,
    youtubePlayer: null,
    playerType: 'audio' // 'audio' or 'youtube'
};

// ==================== INITIALIZATION ====================
// initializeApp() is called by auth.js after successful login


// YouTube IFrame API ready callback
window.onYouTubeIframeAPIReady = function() {
    initYouTubePlayer();
};

function initializeApp() {
    // Load saved data
    loadPlaylists();

    // Setup event listeners
    setupNavigationListeners();
    setupSearchListeners();
    setupPlayerListeners();
    setupPartyModeListeners();
    setupPlaylistListeners();

    // YouTube player is initialized by onYouTubeIframeAPIReady once the IFrame
    // API script finishes loading.
    console.log('🎥 YouTube API mode enabled');

    console.log('🎵 TuneTogether initialized!');
}

function initYouTubePlayer() {
    state.youtubePlayer = new YT.Player('youtube-player', {
        height: '1',
        width: '1',
        playerVars: {
            'autoplay': 0,
            'controls': 0,
            'enablejsapi': 1,
            'origin': window.location.origin,
            'playsinline': 1
        },
        events: {
            'onReady': onYouTubePlayerReady,
            'onStateChange': onYouTubePlayerStateChange,
            'onError': function(e) { console.error('YouTube player error:', e.data); }
        }
    });
}

function onYouTubePlayerReady(event) {
    console.log('✅ YouTube player ready');
    state.youtubePlayer.setVolume(state.volume);
    
    // Update progress for YouTube videos
    setInterval(() => {
        if (state.playerType === 'youtube' && state.youtubePlayer && state.isPlaying) {
            const currentTime = state.youtubePlayer.getCurrentTime();
            const duration = state.youtubePlayer.getDuration();
            if (duration > 0) {
                const percent = (currentTime / duration) * 100;
                const progressFill = document.getElementById('progress-fill');
                const progressInput = document.getElementById('progress-input');
                const currentTimeEl = document.getElementById('current-time');
                const durationEl = document.getElementById('duration');
                
                progressFill.style.width = `${percent}%`;
                progressInput.value = percent;
                currentTimeEl.textContent = formatTime(currentTime);
                durationEl.textContent = formatTime(duration);
            }
        }
    }, 1000);
}

function onYouTubePlayerStateChange(event) {
    // YT.PlayerState.PLAYING = 1, PAUSED = 2, ENDED = 0
    if (event.data === YT.PlayerState.PLAYING) {
        state.isPlaying = true;
        updatePlayButton();
        startVisualizer();
    } else if (event.data === YT.PlayerState.PAUSED) {
        state.isPlaying = false;
        updatePlayButton();
        stopVisualizer();
    } else if (event.data === YT.PlayerState.ENDED) {
        playNext();
    }
}

// ==================== NAVIGATION ====================
function setupNavigationListeners() {
    const navButtons = document.querySelectorAll('.tab-btn, .nav-btn');
    const views = document.querySelectorAll('.view');
    
    navButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            const targetView = btn.dataset.view;
            
            // Update active states
            navButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            
            views.forEach(v => v.classList.remove('active'));
            document.getElementById(`${targetView}-view`).classList.add('active');
        });
    });
}

// ==================== MUSIC SEARCH (Java backend) ====================
// The backend now does both YouTube API hops (search + video details) and all
// title/artist cleanup + duration parsing server-side (see MusicSearchService),
// so this is a single call. Demo-mode fallback stays entirely client-side.
async function searchYouTube(query) {
    console.log('🎥 Searching for:', query);

    try {
        const response = await AUTH.authFetch(`/api/music/search?query=${encodeURIComponent(query)}`);

        if (!response.ok) {
            const errorData = await response.text();
            throw new Error(`Backend API error: ${response.status} - ${errorData}`);
        }

        const results = await response.json();

        if (!results || results.length === 0) {
            console.log('⚠️ No YouTube results found, showing demo tracks');
            return getDemoResults(query);
        }

        console.log(`✅ Found ${results.length} results via backend`);
        return results;

    } catch (error) {
        console.error('❌ Backend search failed:', error);
        console.log('🎵 Falling back to demo mode');
        return getDemoResults(query);
    }
}

// Demo results for testing without API
function getDemoResults(query) {
    const demoTracks = [
        // Pop/Dance
        {
            id: 'demo1',
            title: 'Dancing Queen',
            artist: 'Pop Stars',
            album: 'Party Hits',
            albumArt: 'https://via.placeholder.com/300/ff1493/ffffff?text=Dancing+Queen',
            duration: 230000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3',
            uri: 'demo:track:1'
        },
        {
            id: 'demo2',
            title: 'Blinding Lights',
            artist: 'Night Riders',
            album: 'After Hours',
            albumArt: 'https://via.placeholder.com/300/1a1f3a/ff00ff?text=Blinding+Lights',
            duration: 200000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3',
            uri: 'demo:track:2'
        },
        {
            id: 'demo3',
            title: 'Levitating',
            artist: 'Future Pop',
            album: 'Disco Dreams',
            albumArt: 'https://via.placeholder.com/300/9370db/ffffff?text=Levitating',
            duration: 203000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3',
            uri: 'demo:track:3'
        },
        // Electronic/EDM
        {
            id: 'demo4',
            title: 'Electric Dreams',
            artist: 'Neon Lights',
            album: 'Synthwave Collection',
            albumArt: 'https://via.placeholder.com/300/0a0e27/00f0ff?text=Electric+Dreams',
            duration: 240000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3',
            uri: 'demo:track:4'
        },
        {
            id: 'demo5',
            title: 'Titanium',
            artist: 'EDM Masters',
            album: 'Festival Anthems',
            albumArt: 'https://via.placeholder.com/300/00bfff/ffffff?text=Titanium',
            duration: 245000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3',
            uri: 'demo:track:5'
        },
        {
            id: 'demo6',
            title: 'Midnight City',
            artist: 'Future Funk',
            album: 'Retro Vibes',
            albumArt: 'https://via.placeholder.com/300/1a1f3a/ff00ff?text=Midnight+City',
            duration: 195000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3',
            uri: 'demo:track:6'
        },
        // Rock/Alternative
        {
            id: 'demo7',
            title: 'Thunder Road',
            artist: 'Rock Legends',
            album: 'Highway Songs',
            albumArt: 'https://via.placeholder.com/300/4a2f1a/ff6600?text=Thunder+Road',
            duration: 265000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3',
            uri: 'demo:track:7'
        },
        {
            id: 'demo8',
            title: 'Wonderwall',
            artist: 'Indie Rock',
            album: 'Morning Glory',
            albumArt: 'https://via.placeholder.com/300/8b4513/ffffff?text=Wonderwall',
            duration: 258000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3',
            uri: 'demo:track:8'
        },
        {
            id: 'demo9',
            title: 'Bohemian Rhapsody',
            artist: 'Classic Rock',
            album: 'Opera Night',
            albumArt: 'https://via.placeholder.com/300/2f4f4f/ffffff?text=Bohemian+Rhapsody',
            duration: 354000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3',
            uri: 'demo:track:9'
        },
        // Hip-Hop/Rap
        {
            id: 'demo10',
            title: 'Lose Yourself',
            artist: 'Urban Beats',
            album: '8 Mile Soundtrack',
            albumArt: 'https://via.placeholder.com/300/000000/ff0000?text=Lose+Yourself',
            duration: 326000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3',
            uri: 'demo:track:10'
        },
        {
            id: 'demo11',
            title: 'Sicko Mode',
            artist: 'Trap Stars',
            album: 'Astroworld',
            albumArt: 'https://via.placeholder.com/300/8b008b/ffff00?text=Sicko+Mode',
            duration: 312000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3',
            uri: 'demo:track:11'
        },
        {
            id: 'demo12',
            title: 'HUMBLE',
            artist: 'West Coast',
            album: 'DAMN',
            albumArt: 'https://via.placeholder.com/300/ff0000/000000?text=HUMBLE',
            duration: 177000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-12.mp3',
            uri: 'demo:track:12'
        },
        // R&B/Soul
        {
            id: 'demo13',
            title: 'Superstition',
            artist: 'Soul Legends',
            album: 'Talking Book',
            albumArt: 'https://via.placeholder.com/300/8b4513/ffd700?text=Superstition',
            duration: 245000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-13.mp3',
            uri: 'demo:track:13'
        },
        {
            id: 'demo14',
            title: 'Redbone',
            artist: 'Childish Vibes',
            album: 'Awaken My Love',
            albumArt: 'https://via.placeholder.com/300/ff4500/000000?text=Redbone',
            duration: 327000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-14.mp3',
            uri: 'demo:track:14'
        },
        // Chill/Ambient
        {
            id: 'demo15',
            title: 'Ocean Waves',
            artist: 'Ambient Sounds',
            album: 'Nature Collection',
            albumArt: 'https://via.placeholder.com/300/1a3f5a/0088ff?text=Ocean+Waves',
            duration: 180000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-15.mp3',
            uri: 'demo:track:15'
        },
        {
            id: 'demo16',
            title: 'Summer Breeze',
            artist: 'Chill Vibes',
            album: 'Relaxation Station',
            albumArt: 'https://via.placeholder.com/300/3a4f2a/88ff00?text=Summer+Breeze',
            duration: 220000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-16.mp3',
            uri: 'demo:track:16'
        },
        {
            id: 'demo17',
            title: 'Starlight',
            artist: 'Dream Pop',
            album: 'Cosmic Journey',
            albumArt: 'https://via.placeholder.com/300/1a2f5a/00ccff?text=Starlight',
            duration: 245000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3',
            uri: 'demo:track:17'
        },
        // Jazz/Blues
        {
            id: 'demo18',
            title: 'Take Five',
            artist: 'Jazz Quartet',
            album: 'Time Out',
            albumArt: 'https://via.placeholder.com/300/2f4f4f/ffd700?text=Take+Five',
            duration: 324000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3',
            uri: 'demo:track:18'
        },
        {
            id: 'demo19',
            title: 'Feeling Good',
            artist: 'Nina Style',
            album: 'I Put A Spell',
            albumArt: 'https://via.placeholder.com/300/800080/ffffff?text=Feeling+Good',
            duration: 178000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3',
            uri: 'demo:track:19'
        },
        // Country/Folk
        {
            id: 'demo20',
            title: 'Take Me Home',
            artist: 'Country Roads',
            album: 'Poems Prayers',
            albumArt: 'https://via.placeholder.com/300/8b4513/ffffff?text=Take+Me+Home',
            duration: 193000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3',
            uri: 'demo:track:20'
        },
        {
            id: 'demo21',
            title: 'Wagon Wheel',
            artist: 'Folk Collective',
            album: 'Old Crow',
            albumArt: 'https://via.placeholder.com/300/d2691e/ffffff?text=Wagon+Wheel',
            duration: 190000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3',
            uri: 'demo:track:21'
        },
        // Latin/Reggaeton
        {
            id: 'demo22',
            title: 'Despacito',
            artist: 'Latin Kings',
            album: 'Vida',
            albumArt: 'https://via.placeholder.com/300/ff6347/ffffff?text=Despacito',
            duration: 229000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3',
            uri: 'demo:track:22'
        },
        {
            id: 'demo23',
            title: 'Mi Gente',
            artist: 'J Vibes',
            album: 'Energía',
            albumArt: 'https://via.placeholder.com/300/ff1493/ffff00?text=Mi+Gente',
            duration: 189000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3',
            uri: 'demo:track:23'
        },
        // K-Pop
        {
            id: 'demo24',
            title: 'Dynamite',
            artist: 'BTS Style',
            album: 'BE',
            albumArt: 'https://via.placeholder.com/300/ff69b4/ffffff?text=Dynamite',
            duration: 199000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3',
            uri: 'demo:track:24'
        },
        {
            id: 'demo25',
            title: 'Kill This Love',
            artist: 'Girl Power',
            album: 'Kill This Love',
            albumArt: 'https://via.placeholder.com/300/ff1493/000000?text=Kill+This+Love',
            duration: 191000,
            previewUrl: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3',
            uri: 'demo:track:25'
        }
    ];
    
    // Filter based on query (search in title, artist, or album)
    if (query && query.trim()) {
        const searchTerm = query.toLowerCase().trim();
        const filtered = demoTracks.filter(track => 
            track.title.toLowerCase().includes(searchTerm) ||
            track.artist.toLowerCase().includes(searchTerm) ||
            track.album.toLowerCase().includes(searchTerm)
        );
        
        console.log(`Search for "${query}" found ${filtered.length} results`);
        
        // Return filtered results, or empty array if no matches
        return filtered;
    }
    
    // Return all tracks if no query
    return demoTracks;
}

// ==================== SEARCH ====================
function setupSearchListeners() {
    const searchInput = document.getElementById('search-input');
    const searchBtn = document.getElementById('search-btn');
    
    searchBtn.addEventListener('click', performSearch);
    searchInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            performSearch();
        }
    });
}

async function performSearch() {
    console.log('🎬 performSearch STARTED');
    const searchInput = document.getElementById('search-input');
    const query = searchInput.value.trim();
    console.log('🎬 Search query:', query);
    
    if (!query) {
        alert('Please enter a search term');
        return;
    }
    
    const resultsContainer = document.getElementById('search-results');
    resultsContainer.innerHTML = '<div class="results-placeholder"><div class="placeholder-icon">🔍</div><p>Searching YouTube for "' + query + '"...</p></div>';
    
    try {
        console.log('🎬 Calling searchYouTube...');
        const results = await searchYouTube(query);
        console.log('🎬 Got results:', results);
        console.log('🎬 Results length:', results ? results.length : 0);
        
        if (!results || results.length === 0) {
            resultsContainer.innerHTML = '<div class="results-placeholder"><div class="placeholder-icon">😔</div><p>No results found for "' + query + '"<br><small>Try different keywords</small></p></div>';
            return;
        }
        
        console.log('🎬 Displaying results...');
        displaySearchResults(results);
    } catch (error) {
        console.error('❌ Search failed:', error);
        resultsContainer.innerHTML = '<div class="results-placeholder"><div class="placeholder-icon">❌</div><p>Search failed: ' + error.message + '<br><small>Please check your API key or try again</small></p></div>';
    }
}

function displaySearchResults(results) {
    console.log('🎨 displaySearchResults called with', results.length, 'results');
    const resultsContainer = document.getElementById('search-results');
    
    if (results.length === 0) {
        resultsContainer.innerHTML = '<div class="results-placeholder"><div class="placeholder-icon">🔍</div><p>No results found</p></div>';
        return;
    }
    
    console.log('🎨 First result:', results[0]);
    
    // Store latest search results in state to avoid JSON-in-HTML parsing edge cases.
    state.searchResults = results;

    resultsContainer.innerHTML = results.map((track, index) => {
        const safeTitle = escapeHtml(track.title || 'Unknown Title');
        const safeArtist = escapeHtml(track.artist || 'Unknown Artist');
        const safeAlbumArt = escapeHtml(track.albumArt || '');

        return `
        <div class="result-item" data-index="${index}">
            <img src="${safeAlbumArt}" alt="${safeTitle}" class="result-album-art" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%2250%22 font-size=%2250%22>🎵</text></svg>'">
            <div class="result-info">
                <div class="result-title">${safeTitle}</div>
                <div class="result-artist">${safeArtist}</div>
            </div>
            <div class="result-duration">${formatDuration(track.duration)}</div>
            <button class="add-to-queue-btn" data-index="${index}" title="Add to Queue" style="background:var(--glass2);border:none;color:var(--text);border-radius:6px;cursor:pointer;padding:6px;display:${state.partyRoom ? 'block' : 'none'};margin-left:8px;transition:all 0.2s;">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            </button>
        </div>
    `;
    }).join('');
    
    console.log('🎨 Results HTML generated');
    
    // Add click listeners to results
    document.querySelectorAll('.result-item').forEach(item => {
        item.addEventListener('click', (e) => {
            const index = parseInt(item.dataset.index, 10);
            const track = state.searchResults[index];
            if (!track) return;
            
            // Check if queue button was clicked
            if (e.target.closest('.add-to-queue-btn')) {
                e.stopPropagation();
                addToPartyQueue(track);
                return;
            }
            
            state.currentIndex = index;
            console.log('🎵 Track clicked:', track.title);
            playTrack(track);
        });
    });
    
    // Store results in queue
    state.queue = results;
    console.log('🎨 Results displayed successfully');
}

// ==================== MUSIC PLAYER ====================
function setupPlayerListeners() {
    const audio = document.getElementById('audio-player');
    const playBtn = document.getElementById('play-btn');
    const prevBtn = document.getElementById('prev-btn');
    const nextBtn = document.getElementById('next-btn');
    const progressInput = document.getElementById('progress-input');
    const volumeSlider = document.getElementById('volume-slider');
    const volumeBtn = document.getElementById('volume-btn');
    const likeBtn = document.getElementById('like-btn');
    const addToPlaylistBtn = document.getElementById('add-to-playlist-btn');
    
    // Play/Pause
    playBtn.addEventListener('click', togglePlayPause);
    
    // Previous/Next
    prevBtn.addEventListener('click', playPrevious);
    nextBtn.addEventListener('click', playNext);
    
    // Like & Add to Playlist
    if (likeBtn) likeBtn.addEventListener('click', handleLikeTrack);
    if (addToPlaylistBtn) addToPlaylistBtn.addEventListener('click', openSelectPlaylistModal);
    
    // Progress bar
    progressInput.addEventListener('input', (e) => {
        if (state.playerType === 'youtube' && state.youtubePlayer) {
            const duration = state.youtubePlayer.getDuration();
            const time = (e.target.value / 100) * duration;
            state.youtubePlayer.seekTo(time, true);
        } else {
            const time = (e.target.value / 100) * audio.duration;
            audio.currentTime = time;
        }
    });
    
    // Volume
    volumeSlider.addEventListener('input', (e) => {
        const volume = e.target.value;
        audio.volume = volume / 100;
        state.volume = volume;
        if (state.youtubePlayer) {
            state.youtubePlayer.setVolume(volume);
        }
    });
    
    volumeBtn.addEventListener('click', () => {
        if (audio.volume > 0) {
            audio.volume = 0;
            volumeSlider.value = 0;
            if (state.youtubePlayer) {
                state.youtubePlayer.setVolume(0);
            }
        } else {
            audio.volume = state.volume / 100;
            volumeSlider.value = state.volume;
            if (state.youtubePlayer) {
                state.youtubePlayer.setVolume(state.volume);
            }
        }
    });
    
    // Audio events
    audio.addEventListener('timeupdate', updateProgress);
    audio.addEventListener('ended', playNext);
    audio.addEventListener('play', () => {
        state.isPlaying = true;
        updatePlayButton();
        startVisualizer();
    });
    audio.addEventListener('pause', () => {
        state.isPlaying = false;
        updatePlayButton();
        stopVisualizer();
    });
    
    // Set initial volume
    audio.volume = state.volume / 100;
}

function playTrack(track) {
    const audio = document.getElementById('audio-player');
    
    state.currentTrack = track;
    state.isPlaying = true;
    
    // Update UI
    document.getElementById('album-art').src = track.albumArt || '';
    document.getElementById('track-title').textContent = track.title;
    document.getElementById('track-artist').textContent = track.artist;
    
    // Play based on track type
    if (track.youtubeId && state.youtubePlayer) {
        // YouTube track - show iframe so browser allows audio playback
        state.playerType = 'youtube';
        audio.pause();
        audio.src = '';
        const ytContainer = document.getElementById('youtube-player-container');
        if (ytContainer) {
            ytContainer.style.cssText = 'position:fixed;bottom:80px;left:8px;width:160px;height:90px;z-index:55;border-radius:8px;overflow:hidden;opacity:1;box-shadow:0 4px 16px rgba(0,0,0,0.5);';
        }
        state.youtubePlayer.loadVideoById(track.youtubeId);
        state.youtubePlayer.playVideo();
        console.log('▶️ Playing YouTube video:', track.title);
    } else if (track.previewUrl && !track.previewUrl.includes('youtube.com')) {
        // Audio track (demo or Spotify preview)
        state.playerType = 'audio';
        if (state.youtubePlayer) {
            try { state.youtubePlayer.pauseVideo(); } catch(e) {}
        }
        // Hide YouTube mini-player
        const ytContainer = document.getElementById('youtube-player-container');
        if (ytContainer) {
            ytContainer.style.cssText = 'position:fixed;bottom:0;right:0;width:2px;height:2px;opacity:0.01;pointer-events:none;z-index:-1;';
        }
        audio.src = track.previewUrl;
        audio.play().catch(e => console.warn('Autoplay blocked:', e.message));
        console.log('▶️ Playing audio track:', track.title);
    } else {
        console.error('❌ No valid source for track:', track);
        return;
    }
    
    updatePlayButton();
    updateLikeButtonState();
    
    // Sync with party room if active
    if (state.partyRoom && state.isHost) {
        syncPartyPlayback();
    }
}

function togglePlayPause() {
    if (state.playerType === 'youtube' && state.youtubePlayer) {
        const playerState = state.youtubePlayer.getPlayerState();
        if (playerState === YT.PlayerState.PLAYING) {
            state.youtubePlayer.pauseVideo();
        } else {
            state.youtubePlayer.playVideo();
        }
    } else {
        const audio = document.getElementById('audio-player');
        if (state.isPlaying) {
            audio.pause();
        } else {
            audio.play();
        }
    }
    
    // Sync with party mode
    if (state.partyRoom && state.isHost) {
        setTimeout(() => syncPartyPlayback(state.isPlaying ? 'PLAY' : 'PAUSE'), 100);
    }
}

function playPrevious() {
    if (state.queue.length === 0) return;
    
    state.currentIndex = (state.currentIndex - 1 + state.queue.length) % state.queue.length;
    playTrack(state.queue[state.currentIndex]);
    
    // Sync with party mode
    if (state.partyRoom && state.isHost) {
        syncPartyPlayback();
    }
}

function playNext() {
    // If in party mode, pop from party queue. The host decides locally (same as
    // the original), then tells the server so its queue copy - and everyone
    // else's queue view - shrinks too.
    if (state.partyRoom && state.isHost && state.partyQueue.length > 0) {
        const nextTrack = state.partyQueue.shift();
        renderPartyQueue();
        playTrack(nextTrack);
        if (state.stompClient) {
            state.stompClient.publish({ destination: `/app/room/${state.partyRoom}/queue/next`, body: '{}' });
        }
        syncPartyPlayback();
        return;
    }

    if (state.queue.length === 0) return;
    
    state.currentIndex = (state.currentIndex + 1) % state.queue.length;
    playTrack(state.queue[state.currentIndex]);
    
    // Sync with party mode
    if (state.partyRoom && state.isHost) {
        syncPartyPlayback();
    }
}

function updateProgress() {
    const audio = document.getElementById('audio-player');
    const progressFill = document.getElementById('progress-fill');
    const progressInput = document.getElementById('progress-input');
    const currentTime = document.getElementById('current-time');
    const duration = document.getElementById('duration');
    
    if (audio.duration) {
        const percent = (audio.currentTime / audio.duration) * 100;
        progressFill.style.width = `${percent}%`;
        progressInput.value = percent;
        
        currentTime.textContent = formatTime(audio.currentTime);
        duration.textContent = formatTime(audio.duration);
    }
}

function updatePlayButton() {
    const playIcon = document.querySelector('.play-icon');
    const pauseIcon = document.querySelector('.pause-icon');
    
    if (state.isPlaying) {
        playIcon.style.display = 'none';
        pauseIcon.style.display = 'block';
    } else {
        playIcon.style.display = 'block';
        pauseIcon.style.display = 'none';
    }
}

// ==================== VISUALIZER ====================
let visualizerInterval = null;

function startVisualizer() {
    if (visualizerInterval) {
        clearInterval(visualizerInterval);
        visualizerInterval = null;
    }

    const bars = document.querySelectorAll('.visualizer-bar');
    
    visualizerInterval = setInterval(() => {
        bars.forEach(bar => {
            const height = Math.random() * 60 + 20;
            bar.style.height = `${height}px`;
        });
    }, 100);
}

function stopVisualizer() {
    if (visualizerInterval) {
        clearInterval(visualizerInterval);
        visualizerInterval = null;
    }
    
    const bars = document.querySelectorAll('.visualizer-bar');
    bars.forEach(bar => {
        bar.style.height = '20px';
    });
}

// ==================== PARTY MODE (Java backend: REST + WebSocket/STOMP) ====================
// Real-time sync now goes over a STOMP connection to the Java backend instead of
// Firebase Realtime Database (+ a localStorage-polling fallback when Firebase
// wasn't configured). Room creation/joining are plain REST calls; ongoing sync
// (playback, queue, chat) is push-based over the WebSocket - no more polling.
let partyTickInterval = null;

function connectPartySocket(roomCode, participantId) {
    const client = new StompJs.Client({
        webSocketFactory: () => new SockJS(CONFIG.WS_URL),
        connectHeaders: {
            Authorization: 'Bearer ' + AUTH.accessToken,
            'X-Room-Code': roomCode,
            'X-Participant-Id': participantId
        },
        reconnectDelay: 4000,
        onConnect: () => {
            client.subscribe(`/topic/room/${roomCode}/state`, msg => handleRoomStateMessage(JSON.parse(msg.body)));
            client.subscribe(`/topic/room/${roomCode}/queue`, msg => handleQueueMessage(JSON.parse(msg.body)));
            client.subscribe(`/topic/room/${roomCode}/chat`, msg => handleChatMessage(JSON.parse(msg.body)));
            console.log('✅ Connected to party room:', roomCode);
        },
        onStompError: frame => console.error('❌ STOMP error:', frame.headers['message'], frame.body),
        onWebSocketError: err => console.error('❌ WebSocket error:', err)
    });
    client.activate();
    return client;
}

function startPartyPositionTicker() {
    stopPartyPositionTicker();
    partyTickInterval = setInterval(() => {
        if (!state.partyRoom || !state.isHost || !state.stompClient || !state.currentTrack) return;
        const currentTime = state.playerType === 'youtube' && state.youtubePlayer && typeof state.youtubePlayer.getCurrentTime === 'function'
            ? (state.youtubePlayer.getCurrentTime() || 0)
            : (document.getElementById('audio-player').currentTime || 0);
        state.stompClient.publish({
            destination: `/app/room/${state.partyRoom}/playback/tick`,
            body: JSON.stringify({ positionSeconds: currentTime })
        });
    }, 4000);
}

function stopPartyPositionTicker() {
    if (partyTickInterval) {
        clearInterval(partyTickInterval);
        partyTickInterval = null;
    }
}

function setupPartyModeListeners() {
    const createRoomBtn = document.getElementById('create-room-btn');
    const joinRoomBtn = document.getElementById('join-room-btn');
    const leaveRoomBtn = document.getElementById('leave-room-btn');
    const copyCodeBtn = document.getElementById('copy-code-btn');
    const sendMessageBtn = document.getElementById('send-message-btn');
    const chatInput = document.getElementById('chat-input');
    const emojiButtons = document.querySelectorAll('.emoji-btn');
    
    createRoomBtn.addEventListener('click', createPartyRoom);
    joinRoomBtn.addEventListener('click', joinPartyRoom);
    leaveRoomBtn.addEventListener('click', leavePartyRoom);
    copyCodeBtn.addEventListener('click', copyRoomCode);
    
    sendMessageBtn.addEventListener('click', sendChatMessage);
    chatInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            sendChatMessage();
        }
    });
    
    emojiButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            sendEmojiReaction(btn.dataset.emoji);
        });
    });
}

async function createPartyRoom() {
    console.log('🎉 Creating party room...');
    try {
        const response = await AUTH.authFetch('/api/rooms', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: '{}'
        });
        if (!response.ok) {
            throw new Error(`Could not create room (${response.status})`);
        }
        const { roomCode, participantId } = await response.json();

        state.partyRoom = roomCode;
        state.participantId = participantId;
        state.isHost = true;

        const userName = (typeof AUTH !== 'undefined' && AUTH.user) ? AUTH.user.name : 'Host';
        state.participants = [{ id: participantId, name: userName, isHost: true }];
        state.partyQueue = [];

        // Show party room UI
        document.getElementById('room-section').style.display = 'none';
        document.getElementById('party-room').style.display = 'block';
        document.getElementById('room-code').textContent = roomCode;

        updateParticipantsList();
        renderPartyQueue();

        state.stompClient = connectPartySocket(roomCode, participantId);
        startPartyPositionTicker();

        addChatMessage('System', `🎉 Party room created! Code: ${roomCode}`);
        addChatMessage('System', '💡 Share this code with friends to sync music together!');
    } catch (error) {
        console.error('❌ Failed to create party room:', error);
        showToast('Could not create party room: ' + error.message, 'error');
    }
}

async function joinPartyRoom() {
    console.log('🚪 Joining party room...');
    const roomCodeInput = document.getElementById('room-code-input');
    const roomCode = roomCodeInput.value.trim().toUpperCase();

    if (!roomCode) {
        alert('Please enter a room code');
        return;
    }

    try {
        const response = await AUTH.authFetch(`/api/rooms/${roomCode}/join`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: '{}'
        });

        if (response.status === 404) {
            alert('❌ Room not found. Please check the code and try again.');
            return;
        }
        if (!response.ok) {
            throw new Error(`Could not join room (${response.status})`);
        }

        const { participantId, snapshot } = await response.json();

        state.partyRoom = roomCode;
        state.participantId = participantId;
        state.isHost = snapshot.hostParticipantId === participantId;

        // Show party room UI
        document.getElementById('room-section').style.display = 'none';
        document.getElementById('party-room').style.display = 'block';
        document.getElementById('room-code').textContent = roomCode;

        applyRoomSnapshot(snapshot);

        state.stompClient = connectPartySocket(roomCode, participantId);
        startPartyPositionTicker();

        addChatMessage('System', '✅ Joined the party!');
        console.log('✅ Joined room:', roomCode);
    } catch (error) {
        console.error('❌ Failed to join party room:', error);
        showToast('Could not join party room: ' + error.message, 'error');
    }
}

/** Renders the full room state a joining client gets back in one shot. */
function applyRoomSnapshot(snapshot) {
    const hostId = snapshot.hostParticipantId;
    state.participants = (snapshot.participants || []).map(p => ({ id: p.id, name: p.name, isHost: p.id === hostId }));
    updateParticipantsList();

    state.partyQueue = snapshot.queue || [];
    renderPartyQueue();

    (snapshot.chatHistory || []).forEach(m => {
        const displayName = m.senderParticipantId === state.participantId ? 'You' : m.senderName;
        addChatMessage(displayName, m.text, m.isEmoji);
    });

    if (!state.isHost && snapshot.playback) {
        handlePlaybackState(snapshot.playback);
    }
}

function leavePartyRoom() {
    console.log('👋 Leaving party room...');

    if (state.stompClient) {
        try {
            state.stompClient.publish({ destination: `/app/room/${state.partyRoom}/leave`, body: '{}' });
        } catch (e) {
            // Connection may already be gone - fine, the server reaps on disconnect too.
        }
        state.stompClient.deactivate();
        state.stompClient = null;
    }
    stopPartyPositionTicker();

    state.partyRoom = null;
    state.isHost = false;
    state.participants = [];
    state.participantId = null;
    state.partyQueue = [];

    document.getElementById('room-section').style.display = 'block';
    document.getElementById('party-room').style.display = 'none';
    document.getElementById('room-code-input').value = '';
    document.getElementById('chat-messages').innerHTML = '';

    console.log('✅ Left party room');
}

function copyRoomCode() {
    const roomCode = document.getElementById('room-code').textContent;
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(roomCode).then(() => {
            alert('Room code copied to clipboard!');
        }).catch(() => {
            alert(`Room code: ${roomCode}`);
        });
        return;
    }

    // Fallback for non-HTTPS/local unsupported environments.
    const tempInput = document.createElement('input');
    tempInput.value = roomCode;
    document.body.appendChild(tempInput);
    tempInput.select();
    try {
        document.execCommand('copy');
        alert('Room code copied to clipboard!');
    } catch (error) {
        alert(`Room code: ${roomCode}`);
    } finally {
        document.body.removeChild(tempInput);
    }
}

function updateParticipantsList() {
    const participantsList = document.getElementById('participants-list');
    const participantCount = document.getElementById('participant-count');
    
    participantCount.textContent = state.participants.length;
    
    participantsList.innerHTML = state.participants.map(p => `
        <div class="participant-item">
            <div class="participant-avatar">${escapeHtml((p.name || '?').charAt(0).toUpperCase())}</div>
            <div class="participant-name">${escapeHtml(p.name || 'Guest')}</div>
            ${p.isHost ? '<span class="host-badge">Host</span>' : ''}
        </div>
    `).join('');
}

function sendChatMessage() {
    const chatInput = document.getElementById('chat-input');
    const message = chatInput.value.trim();

    if (!message || !state.stompClient) return;

    state.stompClient.publish({
        destination: `/app/room/${state.partyRoom}/chat`,
        body: JSON.stringify({ text: message, isEmoji: false })
    });
    chatInput.value = '';
    // The message is displayed once the server echoes it back on the /chat
    // topic (handleChatMessage) - same path everyone else's messages take, so
    // there's no separate local-echo copy to keep in sync.
}

function sendEmojiReaction(emoji) {
    if (!state.stompClient) return;
    state.stompClient.publish({
        destination: `/app/room/${state.partyRoom}/chat`,
        body: JSON.stringify({ text: emoji, isEmoji: true })
    });
}

function handleChatMessage(message) {
    const displayName = message.senderParticipantId === state.participantId ? 'You' : message.senderName;
    addChatMessage(displayName, message.text, message.isEmoji);
}

function addChatMessage(sender, message, isEmoji = false) {
    const chatMessages = document.getElementById('chat-messages');
    const messageEl = document.createElement('div');
    messageEl.className = 'chat-message';

    const senderEl = document.createElement('div');
    senderEl.className = 'message-sender';
    senderEl.textContent = sender;

    const contentEl = document.createElement('div');
    contentEl.className = `message-content${isEmoji ? ' message-emoji' : ''}`;
    contentEl.textContent = message;

    messageEl.appendChild(senderEl);
    messageEl.appendChild(contentEl);
    
    chatMessages.appendChild(messageEl);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function syncPartyPlayback(action = 'TRACK_CHANGE') {
    if (!state.partyRoom || !state.isHost || !state.stompClient || !state.currentTrack) return;

    let currentTime = 0;
    if (state.playerType === 'youtube' && state.youtubePlayer && typeof state.youtubePlayer.getCurrentTime === 'function') {
        currentTime = state.youtubePlayer.getCurrentTime() || 0;
    } else {
        currentTime = document.getElementById('audio-player').currentTime || 0;
    }

    state.stompClient.publish({
        destination: `/app/room/${state.partyRoom}/playback`,
        body: JSON.stringify({
            action: action,
            track: state.currentTrack,
            positionSeconds: currentTime
        })
    });
}

function addToPartyQueue(track) {
    if (!state.partyRoom || !state.stompClient) {
        alert('You must be in a party to add to the queue!');
        return;
    }

    state.stompClient.publish({
        destination: `/app/room/${state.partyRoom}/queue/add`,
        body: JSON.stringify({ track })
    });
    showToast(`Added to queue: ${track.title}`, 'success');
    // The queue list re-renders once the server's authoritative copy is
    // broadcast back on /topic/room/{code}/queue (handleQueueMessage).
}

function renderPartyQueue() {
    const queueList = document.getElementById('party-queue-list');
    if (!queueList) return;
    
    if (!state.partyQueue || state.partyQueue.length === 0) {
        queueList.innerHTML = '<p class="queue-empty">No songs in queue yet</p>';
        return;
    }
    
    queueList.innerHTML = state.partyQueue.map((track, index) => {
        const safeTitle = escapeHtml(track.title || 'Unknown');
        const safeArtist = escapeHtml(track.artist || 'Unknown');
        const safeAlbumArt = escapeHtml(track.albumArt || '');
        
        return `
            <div class="participant-item">
                <img src="${safeAlbumArt}" class="participant-avatar" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%2250%22 font-size=%2250%22>🎵</text></svg>'" style="object-fit:cover; border-radius:4px;">
                <div class="participant-name">
                    <div style="font-size:0.8rem; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${safeTitle}</div>
                    <div style="font-size:0.7rem; color:var(--text2); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${safeArtist}</div>
                </div>
            </div>
        `;
    }).join('');
}

function handleQueueMessage(queue) {
    state.partyQueue = queue || [];
    renderPartyQueue();
}

/** Pushed on join/leave/host-change/playback change - see RoomStateMessage on the backend. */
function handleRoomStateMessage(stateMsg) {
    const hostId = stateMsg.hostParticipantId;
    state.participants = (stateMsg.participants || []).map(p => ({ id: p.id, name: p.name, isHost: p.id === hostId }));
    updateParticipantsList();

    state.isHost = state.participantId === hostId;

    if (stateMsg.playback) {
        handlePlaybackState(stateMsg.playback);
    }
}

/**
 * Mirrors the host's playback state. This is the exact same track-change
 * detection / play-pause sync / 2-second seek tolerance the original app used
 * in its Firebase snapshot handler - only the transport changed.
 */
function handlePlaybackState(playback) {
    if (state.isHost || !playback || !playback.currentTrack) return;

    const audio = document.getElementById('audio-player');
    const ytPlayer = state.youtubePlayer;

    // Check if track changed
    if (!state.currentTrack || state.currentTrack.id !== playback.currentTrack.id) {
        console.log('🎵 Syncing track:', playback.currentTrack.title);
        playTrack(playback.currentTrack);
    }

    // Sync play/pause state
    if (playback.isPlaying !== state.isPlaying) {
        console.log('⏯️ Syncing play state:', playback.isPlaying);

        if (state.playerType === 'youtube' && ytPlayer) {
            if (playback.isPlaying) {
                ytPlayer.playVideo();
            } else {
                ytPlayer.pauseVideo();
            }
        } else {
            if (playback.isPlaying) {
                audio.play().catch(e => console.log('Auto-play prevented:', e));
            } else {
                audio.pause();
            }
        }
        state.isPlaying = playback.isPlaying;
        updatePlayButton();
    }

    // Sync seek position (within 2 second tolerance)
    const localCurrentTime =
        state.playerType === 'youtube' && ytPlayer && typeof ytPlayer.getCurrentTime === 'function'
            ? (ytPlayer.getCurrentTime() || 0)
            : (audio.currentTime || 0);

    if (playback.currentTimeSeconds && Math.abs(localCurrentTime - playback.currentTimeSeconds) > 2) {
        console.log('⏩ Syncing position:', playback.currentTimeSeconds);
        if (state.playerType === 'youtube' && ytPlayer) {
            ytPlayer.seekTo(playback.currentTimeSeconds, true);
        } else {
            audio.currentTime = playback.currentTimeSeconds;
        }
    }
}

// ==================== PLAYLISTS ====================
function setupPlaylistListeners() {
    const createPlaylistBtn = document.getElementById('create-playlist-btn');
    const savePlaylistBtn = document.getElementById('save-playlist-btn');
    const modalClose = document.getElementById('modal-close');
    const modal = document.getElementById('playlist-modal');
    
    createPlaylistBtn.addEventListener('click', () => {
        modal.classList.add('active');
    });
    
    modalClose.addEventListener('click', () => {
        modal.classList.remove('active');
    });
    
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.classList.remove('active');
        }
    });
    
    savePlaylistBtn.addEventListener('click', createPlaylist);
    
    // Select Playlist Modal
    const selectModalClose = document.getElementById('select-modal-close');
    const selectModal = document.getElementById('select-playlist-modal');
    
    if (selectModalClose) {
        selectModalClose.addEventListener('click', () => {
            selectModal.classList.remove('active');
        });
    }
    
    if (selectModal) {
        selectModal.addEventListener('click', (e) => {
            if (e.target === selectModal) {
                selectModal.classList.remove('active');
            }
        });
    }
}

async function loadPlaylists() {
    if (typeof AUTH !== 'undefined' && AUTH.user && !AUTH.user.isGuest) {
        try {
            const response = await AUTH.authFetch('/api/library/playlists');
            state.playlists = response.ok ? await response.json() : [];
        } catch (e) {
            console.error('Failed to load playlists:', e);
            state.playlists = [];
        }
        displayPlaylists();
    } else {
        // Not logged in or guest
        state.playlists = [];
        const playlistsGrid = document.getElementById('playlists-grid');
        if (playlistsGrid) {
            playlistsGrid.innerHTML = `
                <div class="playlist-placeholder" style="grid-column: 1/-1;">
                    <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" opacity="0.3"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                    <p>Sign in with Google</p>
                    <span>You must be logged in with Google to create and save playlists to your library.</span>
                </div>
            `;
        }
    }
}

async function createPlaylist() {
    if (typeof AUTH === 'undefined' || !AUTH.user || AUTH.user.isGuest) {
        showToast('Sign in with Google to create playlists', 'error');
        document.getElementById('playlist-modal').classList.remove('active');
        return;
    }

    const nameInput = document.getElementById('playlist-name-input');
    const name = nameInput.value.trim();

    if (!name) {
        alert('Please enter a playlist name');
        return;
    }

    try {
        const response = await AUTH.authFetch('/api/library/playlists', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name })
        });
        if (!response.ok) throw new Error(`Request failed: ${response.status}`);
        const playlist = await response.json();
        state.playlists.push(playlist);
        displayPlaylists();
    } catch (e) {
        console.error('Failed to create playlist:', e);
        showToast('Could not create playlist: ' + e.message, 'error');
    }

    document.getElementById('playlist-modal').classList.remove('active');
    nameInput.value = '';
}

function displayPlaylists() {
    const playlistsGrid = document.getElementById('playlists-grid');
    
    if (state.playlists.length === 0) {
        playlistsGrid.innerHTML = '<div class="playlist-placeholder"><div class="placeholder-icon">🎵</div><p>No playlists yet. Create your first one!</p></div>';
        return;
    }
    
    playlistsGrid.innerHTML = state.playlists.map(playlist => `
        <div class="playlist-card" data-id="${playlist.id}">
            <div class="playlist-cover">🎵</div>
            <div class="playlist-name">${escapeHtml(playlist.name || 'Untitled Playlist')}</div>
            <div class="playlist-count">${playlist.tracks.length} songs</div>
        </div>
    `).join('');
    
    // Add click listeners
    document.querySelectorAll('.playlist-card').forEach(card => {
        card.addEventListener('click', () => {
            const playlistId = parseInt(card.dataset.id);
            openPlaylist(playlistId);
        });
    });
}

function openPlaylist(playlistId) {
    const playlist = state.playlists.find(p => p.id === playlistId);
    if (!playlist) return;
    
    // Hide grid, show detail view
    document.getElementById('playlists-grid').style.display = 'none';
    const detailContainer = document.getElementById('playlist-detail-container');
    detailContainer.style.display = 'block';
    
    document.getElementById('detail-playlist-title').textContent = playlist.name;
    
    const tracksList = document.getElementById('playlist-tracks-list');
    
    if (playlist.tracks.length === 0) {
        tracksList.innerHTML = '<div class="results-placeholder"><p>This playlist is empty</p></div>';
    } else {
        tracksList.innerHTML = playlist.tracks.map((track, index) => {
            const safeTitle = escapeHtml(track.title || 'Unknown Title');
            const safeArtist = escapeHtml(track.artist || 'Unknown Artist');
            const safeAlbumArt = escapeHtml(track.albumArt || '');

            return `
            <div class="result-item" data-index="${index}">
                <img src="${safeAlbumArt}" class="result-album-art" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%2250%22 font-size=%2250%22>🎵</text></svg>'">
                <div class="result-info">
                    <div class="result-title">${safeTitle}</div>
                    <div class="result-artist">${safeArtist}</div>
                </div>
                <div class="result-duration">${formatDuration(track.duration)}</div>
            </div>
            `;
        }).join('');
        
        // Add listeners
        tracksList.querySelectorAll('.result-item').forEach(item => {
            item.addEventListener('click', () => {
                const idx = parseInt(item.dataset.index, 10);
                state.queue = playlist.tracks;
                state.currentIndex = idx;
                playTrack(playlist.tracks[idx]);
            });
        });
    }
    
    // Setup Play All button
    const playAllBtn = document.getElementById('play-all-btn');
    playAllBtn.onclick = () => {
        if (playlist.tracks.length > 0) {
            state.queue = playlist.tracks;
            state.currentIndex = 0;
            playTrack(playlist.tracks[0]);
        }
    };
    
    // Setup Back button
    document.getElementById('back-to-playlists-btn').onclick = () => {
        detailContainer.style.display = 'none';
        document.getElementById('playlists-grid').style.display = 'grid';
        displayPlaylists();
    };
}

async function handleLikeTrack() {
    if (!state.currentTrack) {
        showToast('No track is playing', 'error');
        return;
    }

    if (typeof AUTH === 'undefined' || !AUTH.user || AUTH.user.isGuest) {
        showToast('Sign in with Google to use Liked Songs', 'error');
        return;
    }

    try {
        const response = await AUTH.authFetch('/api/library/liked/tracks', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ track: state.currentTrack })
        });
        if (!response.ok) throw new Error(`Request failed: ${response.status}`);
        const { liked } = await response.json();

        await loadPlaylists();
        showToast(liked ? 'Added to Liked Songs ❤️' : 'Removed from Liked Songs 💔', 'success');
        updateLikeButtonState();
    } catch (e) {
        console.error('Failed to toggle Liked Songs:', e);
        showToast('Could not update Liked Songs: ' + e.message, 'error');
    }
}

function updateLikeButtonState() {
    const likeBtn = document.getElementById('like-btn');
    if (!likeBtn) return;

    const likedPlaylist = state.playlists.find(p => p.liked);
    if (likedPlaylist && state.currentTrack && likedPlaylist.tracks.some(t => t.id === state.currentTrack.id)) {
        likeBtn.style.color = 'var(--pink)';
        const svg = likeBtn.querySelector('svg');
        if (svg) {
            svg.setAttribute('fill', 'var(--pink)');
            svg.setAttribute('stroke', 'var(--pink)');
        }
    } else {
        likeBtn.style.color = 'var(--text2)';
        const svg = likeBtn.querySelector('svg');
        if (svg) {
            svg.setAttribute('fill', 'none');
            svg.setAttribute('stroke', 'currentColor');
        }
    }
}

function openSelectPlaylistModal() {
    if (!state.currentTrack) {
        showToast('No track is playing', 'error');
        return;
    }
    
    if (typeof AUTH === 'undefined' || !AUTH.user || AUTH.user.isGuest) {
        showToast('Sign in with Google to add to playlists', 'error');
        return;
    }
    
    const modal = document.getElementById('select-playlist-modal');
    const list = document.getElementById('playlist-selection-list');
    
    if (state.playlists.length === 0) {
        list.innerHTML = '<p style="color:var(--text3);text-align:center;">No playlists available. Create one first!</p>';
    } else {
        list.innerHTML = state.playlists.map(p => `
            <button class="playlist-select-item" data-id="${p.id}" style="padding:12px; background:var(--bg3); border:1px solid var(--border); border-radius:8px; color:var(--text); text-align:left; cursor:pointer; display:flex; justify-content:space-between; align-items:center;">
                <span>${escapeHtml(p.name)}</span>
                <span style="font-size:0.8rem; color:var(--text2);">${p.tracks.length} songs</span>
            </button>
        `).join('');
        
        list.querySelectorAll('.playlist-select-item').forEach(btn => {
            btn.addEventListener('click', () => {
                const playlistId = parseInt(btn.dataset.id, 10);
                addTrackToPlaylist(playlistId, state.currentTrack);
                modal.classList.remove('active');
            });
            
            // Hover effect
            btn.addEventListener('mouseover', () => btn.style.borderColor = 'var(--accent)');
            btn.addEventListener('mouseout', () => btn.style.borderColor = 'var(--border)');
        });
    }
    
    modal.classList.add('active');
}

async function addTrackToPlaylist(playlistId, track) {
    const playlistBefore = state.playlists.find(p => p.id === playlistId);

    try {
        const response = await AUTH.authFetch(`/api/library/playlists/${playlistId}/tracks`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ track })
        });

        if (response.status === 409) {
            showToast(`Already in ${playlistBefore ? playlistBefore.name : 'playlist'}`, 'error');
            return;
        }
        if (!response.ok) throw new Error(`Request failed: ${response.status}`);

        const updatedPlaylist = await response.json();
        const index = state.playlists.findIndex(p => p.id === playlistId);
        if (index >= 0) {
            state.playlists[index] = updatedPlaylist;
        }
        displayPlaylists();
        showToast(`Added to ${updatedPlaylist.name}`, 'success');
    } catch (e) {
        console.error('Failed to add track to playlist:', e);
        showToast('Could not add track: ' + e.message, 'error');
    }
}

// ==================== UTILITY FUNCTIONS ====================
function formatDuration(ms) {
    const seconds = Math.floor(ms / 1000);
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
}

function formatTime(seconds) {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// ==================== SERVICE WORKER (for PWA support) ====================
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/service-worker.js')
            .then(reg => console.log('✅ Service Worker registered'))
            .catch(err => console.log('❌ Service Worker registration failed'));
    });
}

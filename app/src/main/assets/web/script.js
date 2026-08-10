let currentLang = 'en';
let translations = {};
let allStations = [];
let isUpdating = false;
let stationsLoaded = false;
let lastStatusData = null;
let updateSocket = null;
let authToken = localStorage.getItem('radio-remote-token') || '';
const STATE_IDLE = 1;
const STATE_BUFFERING = 2;
const STATE_READY = 3;
const STATE_ENDED = 4;

function t(key) {
    return (translations[currentLang] && translations[currentLang][key]) ||
           (translations['en'] && translations['en'][key]) || key;
}

function updateUILanguage() {
    document.querySelectorAll('[data-t]').forEach(el => {
        const key = el.getAttribute('data-t');
        const text = t(key);
        if (el.tagName === 'INPUT' && el.placeholder) {
            el.placeholder = text;
        } else {
            el.innerText = text;
        }
    });
}

async function loadTranslations() {
    try {
        const res = await fetch('/translations.json');
        translations = await res.json();
        updateUILanguage();
    } catch (e) {
        console.error("Failed to load translations", e);
    }
}

function updateStatusUI(data) {
    if (!data) return;
    lastStatusData = data;
    const statusEl = document.getElementById('status');
    const currentNameEl = document.getElementById('currentName');

    if (!statusEl || !currentNameEl) return;

    const playPauseIcon = document.getElementById('playPauseIcon');
    const currentStarEl = document.getElementById('currentStar');
    const currentMetadataEl = document.getElementById('currentMetadata');
    const currentImageEl = document.getElementById('currentImage');
    const themeSelect = document.getElementById('themeSelect');
    const langSelect = document.getElementById('langSelect');

    if (data.error) {
        statusEl.innerText = t('status_error');
        currentNameEl.innerText = data.error;
        return;
    }

    let statusText = data.isPlaying ? t('status_playing') : t('status_paused');
    if (data.playWhenReady && !data.isPlaying) {
        statusText = t('status_buffering');
    }
    if (data.playbackState === STATE_BUFFERING) {
        statusText = t('status_buffering');
    }
    statusEl.innerText = statusText;
    if (playPauseIcon) {
        playPauseIcon.innerText = (data.isPlaying || data.playWhenReady) ? 'pause' : 'play_arrow';
    }

    const stationUuid = data.currentStationUuid;
    updateActiveStation(stationUuid);

    const station = allStations.find(s => s.uuid === stationUuid);
    if (station) {
        currentNameEl.innerText = station.name;
        if (currentStarEl) currentStarEl.classList.toggle('hidden', !data.starred);
        if (currentMetadataEl) currentMetadataEl.innerText = data.metadata || "";
        if (currentImageEl) {
            let imageUrl = station.hasImage ? '/api/image/' + station.uuid : 'favicon.png';
            if (station.hasImage && authToken) {
                imageUrl += '?token=' + encodeURIComponent(authToken);
                imageUrl += station.lastModified ? '&t=' + station.lastModified : '';
            } else if (station.hasImage) {
                imageUrl += station.lastModified ? '?t=' + station.lastModified : '';
            }
            currentImageEl.src = imageUrl;
        }
    } else {
        if (stationsLoaded) {
            if (allStations.length === 0) {
                currentNameEl.innerText = t('status_no_stations_available');
            } else {
                currentNameEl.innerText = (data.isPlaying || data.playWhenReady) ? t('status_unknown_station') : t('status_no_station');
            }
        } else {
            currentNameEl.innerText = t('status_loading');
        }
        if (currentStarEl) currentStarEl.classList.add('hidden');
        if (currentMetadataEl) currentMetadataEl.innerText = "";
        if (currentImageEl) currentImageEl.src = 'favicon.png';
    }

    if (themeSelect && themeSelect.value === 'auto_browser') {
        applyThemeUI(getBrowserTheme());
    }

    if (langSelect && langSelect.value === 'auto_browser') {
        const browserLang = navigator.language.split('-')[0];
        const targetLang = translations[browserLang] ? browserLang : 'en';
        if (targetLang !== currentLang) {
            currentLang = targetLang;
            updateUILanguage();
        }
    }
}

async function apiFetch(url, options = {}) {
    if (authToken) {
        options.headers = options.headers || {};
        options.headers['X-Remote-Key'] = authToken;
    }

    let response = await fetch(url, options);

    if (response.status === 401) {
        const code = prompt(t('prompt_pairing_code') || "Please enter the pairing code:");
        if (code) {
            authToken = code;
            localStorage.setItem('radio-remote-token', authToken);
            options.headers = options.headers || {};
            options.headers['X-Remote-Key'] = authToken;
            response = await fetch(url, options);
            if (response.ok) {
                window.location.reload();
            } else if (response.status === 401) {
                authToken = '';
                localStorage.removeItem('radio-remote-token');
            }
        } else {
            authToken = '';
            localStorage.removeItem('radio-remote-token');
        }
    }
    return response;
}

async function updateStatus() {
    if (isUpdating) return;
    try {
        const response = await apiFetch('/api/status');
        const data = await response.json();
        if (!response.ok) {
            updateStatusUI({ error: data.error || "Service Unavailable" });
            return;
        }
        updateStatusUI(data);
    } catch (e) {
        console.error("Status fetch failed", e);
        updateStatusUI({ error: t('status_connection_error') });
    }
}

function initUpdateSocket() {
    if (updateSocket) {
        updateSocket.close();
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    let wsUrl = protocol + '//' + window.location.host + '/api/updates';
    if (authToken) {
        wsUrl += '?token=' + encodeURIComponent(authToken);
    }

    updateSocket = new WebSocket(wsUrl);

    updateSocket.onmessage = (event) => {
        try {
            const message = JSON.parse(event.data);
            if (message.type === 'status') {
                updateStatusUI(message.data);
            } else if (message.type === 'stations') {
                allStations = message.data;
                stationsLoaded = true;
                renderStationList();
            }
        } catch (e) {
            console.error("Failed to parse WebSocket message", e, event.data);
        }
    };

    updateSocket.onclose = () => {
        console.log("WebSocket closed, retrying in 5s...");
        setTimeout(initUpdateSocket, 5000);
    };

    updateSocket.onerror = (error) => {
        console.error("WebSocket error", error);
    };
}

async function loadStations() {
    try {
        const response = await apiFetch('/api/stations');
        if (!response.ok) {
            document.getElementById('stationList').innerText = t('status_error');
            stationsLoaded = true;
            updateStatus();
            return;
        }
        allStations = await response.json();
        stationsLoaded = true;
        renderStationList();
        updateStatus();
    } catch (e) {
        console.error("Load stations failed", e);
        document.getElementById('stationList').innerText = t('status_connection_error');
        stationsLoaded = true;
        updateStatus();
    }
}

function renderStationList() {
    const list = document.getElementById('stationList');
    if (!list) return;
    list.innerHTML = '';
    if (allStations.length === 0) {
        list.innerText = t('stations_empty');
    } else {
        allStations.forEach(station => {
            const div = document.createElement('div');
            div.className = 'station-item';
            div.id = 'station-' + station.uuid;

            const content = document.createElement('div');
            content.className = 'station-item-content';

            const img = document.createElement('img');
            img.className = 'station-img';
            if (station.hasImage) {
                let imageUrl = '/api/image/' + station.uuid;
                if (authToken) {
                    imageUrl += '?token=' + encodeURIComponent(authToken);
                    imageUrl += station.lastModified ? '&t=' + station.lastModified : '';
                } else {
                    imageUrl += station.lastModified ? '?t=' + station.lastModified : '';
                }
                img.src = imageUrl;
            } else {
                img.src = 'favicon.png';
            }
            content.appendChild(img);

            const name = document.createElement('div');
            name.className = 'station-name';
            name.innerText = station.name || 'Unnamed Station';
            content.appendChild(name);

            if (station.starred) {
                const star = document.createElement('span');
                star.className = 'material-icons starred-icon';
                star.innerText = 'star';
                content.appendChild(star);
            }

            div.appendChild(content);
            div.onclick = () => playStation(station.uuid);
            list.appendChild(div);
        });
    }
    if (lastStatusData) {
        updateActiveStation(lastStatusData.currentStationUuid);
    }
}

function updateActiveStation(uuid) {
    document.querySelectorAll('.station-item').forEach(el => el.classList.remove('active'));
    if (!uuid) return;
    const active = document.getElementById('station-' + uuid);
    if (active) active.classList.add('active');
}

async function playStation(uuid) {
    updateActiveStation(uuid);
    document.getElementById('status').innerText = t('status_starting');
    document.getElementById('playPauseIcon').innerText = 'pause';

    const station = allStations.find(s => s.uuid === uuid);
    if (station) {
        document.getElementById('currentName').innerText = station.name;
        document.getElementById('currentStar').classList.toggle('hidden', !station.starred);
        document.getElementById('currentMetadata').innerText = "";
        document.getElementById('currentImage').src = station.hasImage ? '/api/image/' + station.uuid : 'favicon.png';
    }

    try {
        await apiFetch('/api/play/' + uuid, { method: 'POST' });
    } catch (e) { console.error(e); }
}

document.getElementById('playPauseBtn').onclick = async () => {
    const icon = document.getElementById('playPauseIcon');
    const isPlaying = icon.innerText === 'pause';

    icon.innerText = isPlaying ? 'play_arrow' : 'pause';
    document.getElementById('status').innerText = isPlaying ? t('status_paused') : t('status_playing');

    try {
        if (isPlaying) {
            await apiFetch('/api/pause', { method: 'POST' });
        } else {
            await apiFetch('/api/resume', { method: 'POST' });
        }
    } catch (e) { console.error(e); }
};

document.getElementById('prevBtn').onclick = () => {
    apiFetch('/api/prev', { method: 'POST' });
};
document.getElementById('nextBtn').onclick = () => {
    apiFetch('/api/next', { method: 'POST' });
};

function applyThemeUI(theme) {
    document.body.className = theme + '-theme';
}

function getBrowserTheme() {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
    if (themeSelect.value === 'auto_browser') {
        applyThemeUI(e.matches ? 'dark' : 'light');
    }
});

const themeSelect = document.getElementById('themeSelect');
themeSelect.onchange = () => {
    const val = themeSelect.value;
    localStorage.setItem('radio-remote-theme', val);
    if (val === 'auto_browser') {
        applyThemeUI(getBrowserTheme());
    } else {
        applyThemeUI(val);
    }
};

const langSelect = document.getElementById('langSelect');
langSelect.onchange = () => {
    const val = langSelect.value;
    localStorage.setItem('radio-remote-lang', val);
    if (val === 'auto_browser') {
        const browserLang = navigator.language.split('-')[0];
        currentLang = translations[browserLang] ? browserLang : 'en';
    } else {
        currentLang = val;
    }
    updateUILanguage();
    if (lastStatusData) updateStatusUI(lastStatusData);
    renderStationList();
};

const menuBtn = document.getElementById('menuBtn');
const closeMenuBtn = document.getElementById('closeMenuBtn');
const settingsPanel = document.getElementById('settingsPanel');
const showApiDocsBtn = document.getElementById('showApiDocsBtn');
const backToDashboard = document.getElementById('backToDashboard');
const mainDashboard = document.getElementById('mainDashboard');
const apiDocsView = document.getElementById('apiDocsView');

function toggleMenu(e) {
    if (e) e.stopPropagation();
    settingsPanel.classList.toggle('hidden');
}

menuBtn.onclick = toggleMenu;
closeMenuBtn.onclick = () => settingsPanel.classList.add('hidden');

showApiDocsBtn.onclick = () => {
    mainDashboard.classList.add('hidden');
    apiDocsView.classList.remove('hidden');
    settingsPanel.classList.add('hidden');
};

backToDashboard.onclick = () => {
    apiDocsView.classList.add('hidden');
    mainDashboard.classList.remove('hidden');
};

document.addEventListener('click', (e) => {
    if (!settingsPanel.classList.contains('hidden') &&
        !settingsPanel.contains(e.target) &&
        !menuBtn.contains(e.target)) {
        settingsPanel.classList.add('hidden');
    }
});

async function init() {
    document.getElementById('status').innerText = '...';
    document.getElementById('currentName').innerText = '...';

    await loadTranslations();

    const savedTheme = localStorage.getItem('radio-remote-theme') || 'auto_browser';
    themeSelect.value = savedTheme;
    if (savedTheme === 'auto_browser') {
        applyThemeUI(getBrowserTheme());
    } else {
        applyThemeUI(savedTheme);
    }

    const savedLang = localStorage.getItem('radio-remote-lang') || 'auto_browser';
    langSelect.value = savedLang;
    if (savedLang === 'auto_browser') {
        const browserLang = navigator.language.split('-')[0];
        currentLang = translations[browserLang] ? browserLang : 'en';
    } else {
        currentLang = savedLang;
    }

    updateUILanguage();
    await loadStations();
    initUpdateSocket();
}

init();

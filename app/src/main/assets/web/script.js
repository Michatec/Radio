let currentLang = 'en';
let translations = {};
let allStations = [];
let isUpdating = false;
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

async function updateStatus() {
    if (isUpdating) return;
    const statusEl = document.getElementById('status');
    const playPauseIcon = document.getElementById('playPauseIcon');
    const currentNameEl = document.getElementById('currentName');
    const currentStarEl = document.getElementById('currentStar');
    const currentMetadataEl = document.getElementById('currentMetadata');
    const currentImageEl = document.getElementById('currentImage');

    try {
        const response = await fetch('/api/status');
        if (!response.ok) return;

        const data = await response.json();
        if (data.error) {
            statusEl.innerText = t('status_error') + ': ' + data.error;
        } else {
            let statusText = data.isPlaying ? t('status_playing') : t('status_paused');
            if (data.playWhenReady && !data.isPlaying) {
                statusText = "Buffering...";
            }
            if (data.playbackState === STATE_BUFFERING) {
                statusText = "Buffering...";
            }
            statusEl.innerText = statusText;
            playPauseIcon.innerText = (data.isPlaying || data.playWhenReady) ? 'pause' : 'play_arrow';

            const stationUuid = data.currentStationUuid;
            updateActiveStation(stationUuid);

            const station = allStations.find(s => s.uuid === stationUuid);
            if (station) {
                currentNameEl.innerText = station.name;
                currentStarEl.classList.toggle('hidden', !data.starred);
                currentMetadataEl.innerText = data.metadata || "";
                currentImageEl.src = station.hasImage ? '/api/image/' + station.uuid : 'favicon.png';
            } else {
                currentNameEl.innerText = data.isPlaying ? 'Unknown Station' : 'No Station Selected';
                currentStarEl.classList.add('hidden');
                currentMetadataEl.innerText = "";
                currentImageEl.src = 'favicon.png';
            }

            if (themeSelect.value === 'auto_browser') {
                applyThemeUI(getBrowserTheme());
            }

            if (langSelect.value === 'auto_browser') {
                const browserLang = navigator.language.split('-')[0];
                const targetLang = translations[browserLang] ? browserLang : 'en';
                if (targetLang !== currentLang) {
                    currentLang = targetLang;
                    updateUILanguage();
                }
            }
        }
    } catch (e) {
        console.error("Status fetch failed", e);
    }
}

async function loadStations() {
    const list = document.getElementById('stationList');
    try {
        const response = await fetch('/api/stations');
        if (!response.ok) return;
        allStations = await response.json();
        list.innerHTML = '';
        if (allStations.length === 0) {
            list.innerText = t('stations_empty');
            return;
        }
        allStations.forEach(station => {
            const div = document.createElement('div');
            div.className = 'station-item';
            div.id = 'station-' + station.uuid;

            const content = document.createElement('div');
            content.className = 'station-item-content';

            const img = document.createElement('img');
            img.className = 'station-img';
            if (station.hasImage) {
                img.src = '/api/image/' + station.uuid;
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
        updateStatus();
    } catch (e) {
        console.error("Load stations failed", e);
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
    document.getElementById('status').innerText = "Starting...";
    document.getElementById('playPauseIcon').innerText = 'pause';

    const station = allStations.find(s => s.uuid === uuid);
    if (station) {
        document.getElementById('currentName').innerText = station.name;
        document.getElementById('currentStar').classList.toggle('hidden', !station.starred);
        document.getElementById('currentMetadata').innerText = "";
        document.getElementById('currentImage').src = station.hasImage ? '/api/image/' + station.uuid : 'favicon.png';
    }

    try {
        await fetch('/api/play/' + uuid, { method: 'POST' });
        setTimeout(updateStatus, 300);
    } catch (e) { console.error(e); }
}

document.getElementById('playPauseBtn').onclick = async () => {
    const icon = document.getElementById('playPauseIcon');
    const isPlaying = icon.innerText === 'pause';

    icon.innerText = isPlaying ? 'play_arrow' : 'pause';
    document.getElementById('status').innerText = isPlaying ? t('status_paused') : t('status_playing');

    try {
        if (isPlaying) {
            await fetch('/api/pause', { method: 'POST' });
        } else {
            await fetch('/api/resume', { method: 'POST' });
        }
        setTimeout(updateStatus, 300);
    } catch (e) { console.error(e); }
};

document.getElementById('prevBtn').onclick = () => {
    fetch('/api/prev', { method: 'POST' }).then(() => setTimeout(updateStatus, 300));
};
document.getElementById('nextBtn').onclick = () => {
    fetch('/api/next', { method: 'POST' }).then(() => setTimeout(updateStatus, 300));
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
    updateStatus();
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
    updateStatus();
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
    setInterval(updateStatus, 1000);
}

init();

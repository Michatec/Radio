/**
 * Radio Remote Main Controller
 */

const STATE_IDLE = 1;
const STATE_BUFFERING = 2;
const STATE_READY = 3;
const STATE_ENDED = 4;

const App = {
    state: {
        allStations: [],
        lastStatusData: null,
        isUpdating: false,
        stationsLoaded: false,
        isWebStreaming: false
    },

    init() {
        this.setupEventListeners();

        API.onStatusUpdate = (data) => this.updateStatusUI(data);
        API.onStationsUpdate = (data) => {
            this.state.allStations = data;
            this.state.stationsLoaded = true;
            this.renderStationList();
        };

        this.boot();
    },

    async boot() {
        UI.elements.status.innerText = '...';
        UI.elements.currentName.innerText = '...';

        await UI.loadTranslations();
        this.loadSettings();

        await this.loadStations();
        API.initUpdateSocket();
    },

    loadSettings() {
        const savedTheme = localStorage.getItem('radio-remote-theme') || 'auto_browser';
        UI.elements.themeSelect.value = savedTheme;
        if (savedTheme === 'auto_browser') {
            UI.applyThemeUI(UI.getBrowserTheme());
        } else {
            UI.applyThemeUI(savedTheme);
        }

        const savedLang = localStorage.getItem('radio-remote-lang') || 'auto_browser';
        UI.elements.langSelect.value = savedLang;
        if (savedLang === 'auto_browser') {
            const browserLang = navigator.language.split('-')[0];
            UI.currentLang = UI.translations[browserLang] ? browserLang : 'en';
        } else {
            UI.currentLang = savedLang;
        }

        UI.updateUILanguage();
    },

    setupEventListeners() {
        UI.elements.playPauseBtn.onclick = () => this.togglePlayback();
        UI.elements.prevBtn.onclick = () => API.prev();
        UI.elements.nextBtn.onclick = () => API.next();
        UI.elements.webStreamBtn.onclick = () => this.toggleWebStream();

        UI.elements.themeSelect.onchange = (e) => {
            const val = e.target.value;
            localStorage.setItem('radio-remote-theme', val);
            UI.applyThemeUI(val === 'auto_browser' ? UI.getBrowserTheme() : val);
        };

        UI.elements.langSelect.onchange = (e) => {
            const val = e.target.value;
            localStorage.setItem('radio-remote-lang', val);
            if (val === 'auto_browser') {
                const browserLang = navigator.language.split('-')[0];
                UI.currentLang = UI.translations[browserLang] ? browserLang : 'en';
            } else {
                UI.currentLang = val;
            }
            UI.updateUILanguage();
            if (this.state.lastStatusData) this.updateStatusUI(this.state.lastStatusData);
            this.renderStationList();
        };

        UI.elements.menuBtn.onclick = (e) => {
            e.stopPropagation();
            UI.elements.settingsPanel.classList.toggle('hidden');
        };
        UI.elements.closeMenuBtn.onclick = () => UI.elements.settingsPanel.classList.add('hidden');
        UI.elements.showApiDocsBtn.onclick = () => {
            UI.elements.mainDashboard.classList.add('hidden');
            UI.elements.apiDocsView.classList.remove('hidden');
            UI.elements.settingsPanel.classList.add('hidden');
        };
        UI.elements.backToDashboard.onclick = () => {
            UI.elements.apiDocsView.classList.add('hidden');
            UI.elements.mainDashboard.classList.remove('hidden');
        };

        document.addEventListener('click', (e) => {
            if (!UI.elements.settingsPanel.classList.contains('hidden') &&
                !UI.elements.settingsPanel.contains(e.target) &&
                !UI.elements.menuBtn.contains(e.target)) {
                UI.elements.settingsPanel.classList.add('hidden');
            }
        });

        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
            if (UI.elements.themeSelect.value === 'auto_browser') {
                UI.applyThemeUI(e.matches ? 'dark' : 'light');
            }
        });

        window.onresize = () => {
            UI.checkOverflow(UI.elements.currentName);
            UI.checkOverflow(UI.elements.currentMetadata);
        };
    },

    updateStatusUI(data) {
        if (!data) return;
        const prevStationUuid = this.state.lastStatusData ? this.state.lastStatusData.currentStationUuid : null;
        this.state.lastStatusData = data;

        if (data.error) {
            UI.elements.status.innerText = UI.t('status_error');
            UI.elements.currentName.innerText = data.error;
            UI.checkOverflow(UI.elements.currentName);
            return;
        }

        let statusText = data.isPlaying ? UI.t('status_playing') : UI.t('status_paused');
        if (data.playWhenReady && !data.isPlaying) statusText = UI.t('status_buffering');
        if (data.playbackState === STATE_BUFFERING) statusText = UI.t('status_buffering');

        UI.elements.status.innerText = statusText;
        if (UI.elements.playPauseIcon) {
            UI.elements.playPauseIcon.innerText = (data.isPlaying || data.playWhenReady) ? 'pause' : 'play_arrow';
        }

        this.updateActiveStation(data.currentStationUuid);

        const station = this.state.allStations.find(s => s.uuid === data.currentStationUuid);
        if (station) {
            UI.elements.currentName.innerText = station.name;
            UI.checkOverflow(UI.elements.currentName);
            UI.elements.currentStar.classList.toggle('hidden', !data.starred);
            UI.elements.currentMetadata.innerText = data.metadata || "";
            UI.checkOverflow(UI.elements.currentMetadata);

            let imageUrl = station.hasImage ? `/api/image/${station.uuid}` : 'favicon.png';
            if (station.hasImage) {
                const params = new URLSearchParams();
                if (API.authToken) params.append('token', API.authToken);
                if (station.lastModified) params.append('t', station.lastModified);
                const query = params.toString();
                if (query) imageUrl += '?' + query;
            }
            UI.elements.currentImage.src = imageUrl;
        } else {
            this.handleUnknownStation(data);
        }

        if (this.state.isWebStreaming && prevStationUuid && data.currentStationUuid !== prevStationUuid) {
            this.loadAndPlayWebStream();
        }
    },

    handleUnknownStation(data) {
        if (this.state.stationsLoaded) {
            UI.elements.currentName.innerText = this.state.allStations.length === 0 ?
                UI.t('status_no_stations_available') :
                ((data.isPlaying || data.playWhenReady) ? UI.t('status_unknown_station') : UI.t('status_no_station'));
        } else {
            UI.elements.currentName.innerText = UI.t('status_loading');
        }
        UI.checkOverflow(UI.elements.currentName);
        UI.elements.currentStar.classList.add('hidden');
        UI.elements.currentMetadata.innerText = "";
        UI.elements.currentImage.src = 'favicon.png';
    },

    async loadStations() {
        try {
            this.state.allStations = await API.getStations();
            this.state.stationsLoaded = true;
            this.renderStationList();
            this.updateStatus();
        } catch (e) {
            console.error("Load stations failed", e);
            UI.elements.stationList.innerText = UI.t('status_connection_error');
            this.state.stationsLoaded = true;
            this.updateStatus();
        }
    },

    async updateStatus() {
        if (this.state.isUpdating) return;
        this.state.isUpdating = true;
        try {
            const data = await API.getStatus();
            this.updateStatusUI(data);
        } catch (e) {
            console.error("Status fetch failed", e);
            this.updateStatusUI({ error: UI.t('status_connection_error') });
        } finally {
            this.state.isUpdating = false;
        }
    },

    renderStationList() {
        if (!UI.elements.stationList) return;
        UI.elements.stationList.innerHTML = '';

        if (this.state.allStations.length === 0) {
            UI.elements.stationList.innerText = UI.t('stations_empty');
        } else {
            this.state.allStations.forEach(station => {
                const div = this.createStationItem(station);
                UI.elements.stationList.appendChild(div);
            });
        }
        if (this.state.lastStatusData) {
            this.updateActiveStation(this.state.lastStatusData.currentStationUuid);
        }
    },

    createStationItem(station) {
        const div = document.createElement('div');
        div.className = 'station-item';
        div.id = 'station-' + station.uuid;

        const content = document.createElement('div');
        content.className = 'station-item-content';

        const img = document.createElement('img');
        img.className = 'station-img';
        let imageUrl = station.hasImage ? `/api/image/${station.uuid}` : 'favicon.png';
        if (station.hasImage) {
            const params = new URLSearchParams();
            if (API.authToken) params.append('token', API.authToken);
            if (station.lastModified) params.append('t', station.lastModified);
            const query = params.toString();
            if (query) imageUrl += '?' + query;
        }
        img.src = imageUrl;

        const name = document.createElement('div');
        name.className = 'station-name';
        name.innerText = station.name || 'Unnamed Station';

        content.appendChild(img);
        content.appendChild(name);

        if (station.starred) {
            const star = document.createElement('span');
            star.className = 'material-icons starred-icon';
            star.innerText = 'star';
            content.appendChild(star);
        }

        div.appendChild(content);
        div.onclick = () => this.playStation(station.uuid);
        return div;
    },

    updateActiveStation(uuid) {
        document.querySelectorAll('.station-item').forEach(el => el.classList.remove('active'));
        if (!uuid) return;
        const active = document.getElementById('station-' + uuid);
        if (active) active.classList.add('active');
    },

    async playStation(uuid) {
        this.updateActiveStation(uuid);
        UI.elements.status.innerText = UI.t('status_starting');
        UI.elements.playPauseIcon.innerText = 'pause';

        const station = this.state.allStations.find(s => s.uuid === uuid);
        if (station) {
            UI.elements.currentName.innerText = station.name;
            UI.elements.currentStar.classList.toggle('hidden', !station.starred);
            UI.elements.currentMetadata.innerText = "";
            let imageUrl = station.hasImage ? `/api/image/${station.uuid}` : 'favicon.png';
            if (station.hasImage && station.lastModified) imageUrl += `?t=${station.lastModified}`;
            UI.elements.currentImage.src = imageUrl;
        }

        try {
            await API.playStation(uuid);
        } catch (e) { console.error(e); }
    },

    async togglePlayback() {
        const isPlaying = UI.elements.playPauseIcon.innerText === 'pause';
        UI.elements.playPauseIcon.innerText = isPlaying ? 'play_arrow' : 'pause';
        UI.elements.status.innerText = isPlaying ? UI.t('status_paused') : UI.t('status_playing');

        try {
            if (isPlaying) await API.pause();
            else await API.resume();
        } catch (e) { console.error(e); }
    },

    async toggleWebStream() {
        this.state.isWebStreaming = !this.state.isWebStreaming;
        if (this.state.isWebStreaming) {
            await this.loadAndPlayWebStream();
        } else {
            UI.elements.webAudio.pause();
            UI.elements.webAudio.src = '';
            UI.elements.webStreamIcon.innerText = 'volume_off';
            UI.elements.webStreamBtn.classList.remove('active-stream');
        }
    },

    async loadAndPlayWebStream() {
        try {
            const absoluteStreamUrl = await API.getStreamUrl();
            if (absoluteStreamUrl) {
                UI.elements.webAudio.src = absoluteStreamUrl;
                await UI.elements.webAudio.play();
                UI.elements.webStreamIcon.innerText = 'volume_up';
                UI.elements.webStreamBtn.classList.add('active-stream');
            }
        } catch (e) {
            console.error("Playback failed", e);
            this.state.isWebStreaming = false;
            UI.elements.webStreamIcon.innerText = 'volume_off';
            UI.elements.webStreamBtn.classList.remove('active-stream');
        }
    }
};

App.init();

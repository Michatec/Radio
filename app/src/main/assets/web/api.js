/**
 * Radio Remote API Communication
 */

const API = {
    authToken: localStorage.getItem('radio-remote-token') || '',
    updateSocket: null,
    onStatusUpdate: null,
    onStationsUpdate: null,

    async apiFetch(url, options = {}) {
        if (this.authToken) {
            options.headers = options.headers || {};
            options.headers['X-Remote-Key'] = this.authToken;
        }

        let response = await fetch(url, options);

        if (response.status === 401) {
            const code = prompt(UI.t('prompt_pairing_code') || "Please enter the pairing code:");
            if (code) {
                this.authToken = code;
                localStorage.setItem('radio-remote-token', this.authToken);
                options.headers = options.headers || {};
                options.headers['X-Remote-Key'] = this.authToken;
                response = await fetch(url, options);
                if (response.status === 401) {
                    this.authToken = '';
                    localStorage.removeItem('radio-remote-token');
                }
            } else {
                this.authToken = '';
                localStorage.removeItem('radio-remote-token');
            }
        }
        return response;
    },

    initUpdateSocket() {
        if (this.updateSocket) {
            this.updateSocket.close();
        }

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        let wsUrl = `${protocol}//${window.location.host}/api/updates`;
        if (this.authToken) {
            wsUrl += `?token=${encodeURIComponent(this.authToken)}`;
        }

        this.updateSocket = new WebSocket(wsUrl);

        this.updateSocket.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data);
                if (message.type === 'status' && this.onStatusUpdate) {
                    this.onStatusUpdate(message.data);
                } else if (message.type === 'stations' && this.onStationsUpdate) {
                    this.onStationsUpdate(message.data);
                }
            } catch (e) {
                console.error("Failed to parse WebSocket message", e, event.data);
            }
        };

        this.updateSocket.onclose = () => {
            console.log("WebSocket closed, retrying in 5s...");
            setTimeout(() => this.initUpdateSocket(), 5000);
        };

        this.updateSocket.onerror = (error) => {
            console.error("WebSocket error", error);
        };
    },

    async getStatus() {
        const response = await this.apiFetch('/api/status');
        if (!response.ok) throw new Error(response.statusText);
        return await response.json();
    },

    async getStations() {
        const response = await this.apiFetch('/api/stations');
        if (!response.ok) throw new Error(response.statusText);
        return await response.json();
    },

    async playStation(uuid) {
        return await this.apiFetch(`/api/play/${uuid}`, { method: 'POST' });
    },

    async pause() {
        return await this.apiFetch('/api/pause', { method: 'POST' });
    },

    async resume() {
        return await this.apiFetch('/api/resume', { method: 'POST' });
    },

    async prev() {
        return await this.apiFetch('/api/prev', { method: 'POST' });
    },

    async next() {
        return await this.apiFetch('/api/next', { method: 'POST' });
    },

    async getStreamUrl() {
        let apiUrl = '/api/stream';
        if (this.authToken) {
            apiUrl += `?token=${encodeURIComponent(this.authToken)}`;
        }
        const response = await this.apiFetch(apiUrl);
        if (!response.ok) throw new Error("Failed to get stream url");
        const json = await response.json();
        return json.status;
    }
};

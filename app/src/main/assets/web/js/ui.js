/**
 * Radio Remote UI Management
 */

const UI = {
    elements: {},

    init() {
        this.elements = {
            status: document.getElementById('status'),
            currentName: document.getElementById('currentName'),
            playPauseIcon: document.getElementById('playPauseIcon'),
            playPauseBtn: document.getElementById('playPauseBtn'),
            currentStar: document.getElementById('currentStar'),
            currentMetadata: document.getElementById('currentMetadata'),
            currentImage: document.getElementById('currentImage'),
            themeSelect: document.getElementById('themeSelect'),
            langSelect: document.getElementById('langSelect'),
            stationList: document.getElementById('stationList'),
            webAudio: document.getElementById('webAudio'),
            webStreamIcon: document.getElementById('webStreamIcon'),
            webStreamBtn: document.getElementById('webStreamBtn'),
            settingsPanel: document.getElementById('settingsPanel'),
            menuBtn: document.getElementById('menuBtn'),
            closeMenuBtn: document.getElementById('closeMenuBtn'),
            showApiDocsBtn: document.getElementById('showApiDocsBtn'),
            backToDashboard: document.getElementById('backToDashboard'),
            mainDashboard: document.getElementById('mainDashboard'),
            apiDocsView: document.getElementById('apiDocsView'),
            prevBtn: document.getElementById('prevBtn'),
            nextBtn: document.getElementById('nextBtn')
        };
    },

    translations: {},
    currentLang: 'en',

    t(key) {
        return (this.translations[this.currentLang] && this.translations[this.currentLang][key]) ||
               (this.translations['en'] && this.translations['en'][key]) || key;
    },

    async loadTranslations() {
        try {
            const res = await fetch('/translations.json');
            this.translations = await res.json();
            this.updateUILanguage();
        } catch (e) {
            console.error("Failed to load translations", e);
        }
    },

    updateUILanguage() {
        document.querySelectorAll('[data-t]').forEach(el => {
            const key = el.getAttribute('data-t');
            const text = this.t(key);
            if (el.tagName === 'INPUT' && el.placeholder) {
                el.placeholder = text;
            } else {
                el.innerText = text;
            }
        });
        document.querySelectorAll('[data-t-title]').forEach(el => {
            const key = el.getAttribute('data-t-title');
            el.setAttribute('title', this.t(key));
        });
    },

    checkOverflow(el) {
        if (!el) return;
        el.classList.remove('animate-marquee');
        setTimeout(() => {
            const container = el.parentElement;
            if (container && el.scrollWidth > container.offsetWidth) {
                el.classList.add('animate-marquee');
            }
        }, 50);
    },

    applyThemeUI(theme) {
        document.body.className = theme + '-theme';
    },

    getBrowserTheme() {
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    },

    authModalPromise: null,

    getClientId() {
        let clientId = localStorage.getItem('radio-client-id');
        if (!clientId) {
            clientId = (window.crypto && typeof crypto.randomUUID === 'function')
                ? crypto.randomUUID()
                : this.randomToken(16);
            localStorage.setItem('radio-client-id', clientId);
        }
        return clientId;
    },

    getPairKey() {
        let pairKey = localStorage.getItem('radio-pair-key');
        if (!pairKey) {
            pairKey = this.randomToken(32);
            localStorage.setItem('radio-pair-key', pairKey);
        }
        return pairKey;
    },

    randomToken(length) {
        const bytes = new Uint8Array(length);
        (window.crypto || window.msCrypto).getRandomValues(bytes);
        return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
    },

    loadQrCode(qrImg, clientId, pairHeaders) {
        const codeEl = document.getElementById('authQrCode');
        const query = `clientId=${encodeURIComponent(clientId)}`;
        fetch(`/api/pair/start?${query}`, { method: 'POST', headers: pairHeaders })
            .then(res => res.ok ? res.json() : null)
            .then(data => {
                if (data && typeof data.code === 'string' && codeEl) codeEl.textContent = data.code;
                return fetch(`/api/qr?${query}&t=${Date.now()}`, { headers: pairHeaders });
            })
            .then(res => res && res.ok ? res.blob() : null)
            .then(blob => {
                if (!blob) return;
                if (qrImg && qrImg.dataset.objectUrl) URL.revokeObjectURL(qrImg.dataset.objectUrl);
                if (!qrImg) return;
                const objectUrl = URL.createObjectURL(blob);
                qrImg.dataset.objectUrl = objectUrl;
                qrImg.src = objectUrl;
            })
            .catch(() => {});
    },

    showAuthModal() {
        if (this.authModalPromise) {
            return this.authModalPromise;
        }

        this.authModalPromise = new Promise((resolve) => {
            let modal = document.getElementById('authModal');
            if (!modal) {
                this.authModalPromise = null;
                const code = prompt(this.t('prompt_pairing_code') || 'Please enter the pairing code:');
                return resolve(code ? code.trim() || null : null);
            }

            modal.classList.remove('hidden');
            const tokenBtn = document.getElementById('tabTokenBtn');
            const qrBtn = document.getElementById('tabQrBtn');
            const panelToken = document.getElementById('authPanelToken');
            const panelQr = document.getElementById('authPanelQr');
            const input = document.getElementById('authCodeInput');
            const submitBtn = document.getElementById('authSubmitBtn');

            const clientId = this.getClientId();
            const pairHeaders = { 'X-Pair-Key': this.getPairKey() };

            input.value = '';
            tokenBtn.classList.add('active');
            qrBtn.classList.remove('active');
            panelToken.classList.remove('hidden');
            panelQr.classList.add('hidden');
            setTimeout(() => input.focus(), 100);

            const qrImg = modal.querySelector('.auth-qr-img');
            this.loadQrCode(qrImg, clientId, pairHeaders);

            let pollInterval = setInterval(async () => {
                if (modal.classList.contains('hidden')) {
                    clearInterval(pollInterval);
                    return;
                }
                try {
                    const res = await fetch(`/api/pair/status?clientId=${encodeURIComponent(clientId)}`,
                        { headers: pairHeaders });
                    if (res.ok) {
                        const data = await res.json();
                        const token = data && data.paired && typeof data.token === 'string' ? data.token.trim() : '';
                        if (token) {
                            clearInterval(pollInterval);
                            modal.classList.add('hidden');
                            this.authModalPromise = null;
                            resolve(token);
                        }
                    }
                } catch (e) {
                    // Ignore
                }
            }, 2000);

            tokenBtn.onclick = () => {
                tokenBtn.classList.add('active');
                qrBtn.classList.remove('active');
                panelToken.classList.remove('hidden');
                panelQr.classList.add('hidden');
                input.focus();
            };

            qrBtn.onclick = () => {
                qrBtn.classList.add('active');
                tokenBtn.classList.remove('active');
                panelQr.classList.remove('hidden');
                panelToken.classList.add('hidden');
                this.loadQrCode(qrImg, clientId, pairHeaders);
            };

            const submitHandler = () => {
                const code = input.value.trim();
                clearInterval(pollInterval);
                modal.classList.add('hidden');
                submitBtn.onclick = null;
                input.onkeydown = null;
                this.authModalPromise = null;
                resolve(code || null);
            };

            submitBtn.onclick = submitHandler;
            input.onkeydown = (e) => {
                if (e.key === 'Enter') submitHandler();
            };
        });

        return this.authModalPromise;
    }
};

UI.init();

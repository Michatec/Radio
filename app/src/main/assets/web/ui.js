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
    }
};

UI.init();

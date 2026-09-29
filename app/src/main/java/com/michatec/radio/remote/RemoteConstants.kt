package com.michatec.radio.remote

object RemoteConstants {
    const val PORT = 8080
    const val HOST = "0.0.0.0"
    
    object Routes {
        const val ROOT = "/"
        const val STYLE = "/css/style.css"
        const val SCRIPT = "/js/script.js"
        const val API_SCRIPT = "/js/api.js"
        const val UI_SCRIPT = "/js/ui.js"
        const val TRANSLATIONS = "/translations.json"
        const val FAVICON = "/favicon.png"
        
        const val API_CONFIG = "/api/config"
        const val API_STATUS = "/api/status"
        const val API_STATIONS = "/api/stations"
        const val API_UPDATES = "/api/updates"
        const val API_IMAGE = "/api/image/{uuid}"
        const val API_STREAM = "/api/stream"
        const val API_QR = "/api/qr"
        const val API_PAIR_START = "/api/pair/start"
        const val API_PAIR_STATUS = "/api/pair/status"
        
        const val API_PLAY = "/api/play/{uuid}"
        const val API_PAUSE = "/api/pause"
        const val API_RESUME = "/api/resume"
        const val API_NEXT = "/api/next"
        const val API_PREV = "/api/prev"
    }
    
    object Auth {
        const val HEADER_KEY = "X-Remote-Key"
        const val QUERY_PARAM_TOKEN = "token"
        const val HEADER_PAIR_KEY = "X-Pair-Key"
        const val MAX_FAILED_ATTEMPTS = 10
        const val MAX_TRACKED_HOSTS = 256
        const val LOCKOUT_COOLDOWN_MS = 60_000L
        const val MAX_LOCKOUT_FACTOR = 16
        const val FAILURE_DELAY_MS = 2000L
    }

    object Pairing {
        const val QR_PREFIX = "radio-pair:"
        const val SESSION_TTL_SECONDS = 900L
    }
    
    object WebSocket {
        const val TYPE_STATUS = "status"
        const val TYPE_STATIONS = "stations"
    }
}

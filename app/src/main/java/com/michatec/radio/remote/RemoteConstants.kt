package com.michatec.radio.remote

object RemoteConstants {
    const val PORT = 8080
    const val HOST = "0.0.0.0"
    
    object Routes {
        const val ROOT = "/"
        const val STYLE = "/style.css"
        const val SCRIPT = "/script.js"
        const val TRANSLATIONS = "/translations.json"
        const val FAVICON = "/favicon.png"
        
        const val API_CONFIG = "/api/config"
        const val API_STATUS = "/api/status"
        const val API_STATIONS = "/api/stations"
        const val API_UPDATES = "/api/updates"
        const val API_IMAGE = "/api/image/{uuid}"
        
        const val API_PLAY = "/api/play/{uuid}"
        const val API_PAUSE = "/api/pause"
        const val API_RESUME = "/api/resume"
        const val API_NEXT = "/api/next"
        const val API_PREV = "/api/prev"
    }
    
    object Auth {
        const val HEADER_KEY = "X-Remote-Key"
        const val QUERY_PARAM_TOKEN = "token"
        const val MAX_FAILED_ATTEMPTS = 10
    }
    
    object WebSocket {
        const val TYPE_STATUS = "status"
        const val TYPE_STATIONS = "stations"
    }
}

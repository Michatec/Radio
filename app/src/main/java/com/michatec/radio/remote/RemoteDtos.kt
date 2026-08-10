package com.michatec.radio.remote

data class ConfigResponse(
    val version: String
)

data class StatusResponse(
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
    val playbackState: Int,
    val currentStationUuid: String,
    val metadata: String,
    val starred: Boolean
)

data class StationDto(
    val uuid: String,
    val name: String,
    val hasImage: Boolean,
    val starred: Boolean,
    val lastModified: Long
)

data class WebSocketMessage(
    val type: String,
    val data: Any
)

data class ErrorResponse(
    val error: String
)

data class GenericResponse(
    val status: String = "ok"
)

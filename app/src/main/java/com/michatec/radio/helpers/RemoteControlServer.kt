package com.michatec.radio.helpers

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.michatec.radio.BuildConfig
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.BindException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RemoteControlServer(private val context: Context) {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var player: Player? = null
    private var serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lifecycleMutex = Mutex()
    private val gson = Gson()
    private val _statusUpdates = MutableSharedFlow<Map<String, Any>>(extraBufferCapacity = 1)
    private val statusUpdates = _statusUpdates.asSharedFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            broadcastStatus()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            broadcastStatus()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            broadcastStatus()
        }

        @UnstableApi
        override fun onMetadata(metadata: Metadata) {
            broadcastStatus()
        }
    }

    private suspend fun getStationsList(): List<Map<String, Any>> {
        val collection = onGetCollection?.invoke() ?: withContext(Dispatchers.IO) {
            FileHelper.readCollection(context)
        }
        return collection.stations.map {
            val imageFile = File(context.getExternalFilesDir(""), FileHelper.determineDestinationFolderPath(com.michatec.radio.Keys.FILE_TYPE_IMAGE, it.uuid) + "/" + com.michatec.radio.Keys.STATION_IMAGE_FILE)
            val lastModified = if (imageFile.exists()) imageFile.lastModified() else 0L
            mapOf(
                "uuid" to it.uuid,
                "name" to it.name,
                "hasImage" to it.smallImage.isNotEmpty(),
                "starred" to it.starred,
                "lastModified" to lastModified
            )
        }
    }

    fun notifyCollectionChanged() {
        serverScope.launch {
            try {
                val stations = getStationsList()
                _statusUpdates.emit(mapOf("type" to "stations", "data" to stations))
            } catch (e: Exception) {
                Log.e("RemoteControlServer", "Error broadcasting stations", e)
            }
        }
    }

    private fun broadcastStatus() {
        serverScope.launch {
            val status = getStatusMap()
            if (status != null) {
                _statusUpdates.emit(mapOf("type" to "status", "data" to status))
            }
        }
    }

    private suspend fun getStatusMap(): Map<String, Any>? {
        val p = player ?: return null
        return try {
            val collection = onGetCollection?.invoke() ?: withContext(Dispatchers.IO) {
                FileHelper.readCollection(context)
            }

            withContext(Dispatchers.Main) {
                val metadataHistory = PreferencesHelper.loadMetadataHistory()
                val currentTrack = if (metadataHistory.isNotEmpty()) metadataHistory.last() else ""
                val mediaId = p.currentMediaItem?.mediaId ?: PreferencesHelper.loadLastPlayedStationUuid()

                val station = CollectionHelper.getStation(collection, mediaId)

                mapOf(
                    "isPlaying" to p.isPlaying,
                    "playWhenReady" to p.playWhenReady,
                    "playbackState" to p.playbackState,
                    "currentStationUuid" to mediaId,
                    "metadata" to currentTrack,
                    "starred" to station.starred
                )
            }
        } catch (e: Exception) {
            Log.e("RemoteControlServer", "Error getting status map", e)
            null
        }
    }

    var onPlayStation: ((String) -> Unit)? = null
    var onGetCollection: (() -> com.michatec.radio.core.Collection)? = null
    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun setPlayer(player: Player?) {
        this.player?.removeListener(playerListener)
        this.player = player
        this.player?.addListener(playerListener)
    }

    private fun safeReadAsset(path: String): ByteArray? {
        return try {
            context.assets.open(path).use { it.readBytes() }
        } catch (e: Exception) {
            Log.e("RemoteControlServer", "Failed to read asset: $path", e)
            null
        }
    }

    fun start() {
        serverScope.launch {
            lifecycleMutex.withLock {
                if (server != null) {
                    Log.d("RemoteControlServer", "Server is already running.")
                    return@withLock
                }

                var attempt = 1
                val maxAttempts = 5
                
                while (attempt <= maxAttempts) {
                    try {
                        Log.i("RemoteControlServer", "Starting server (attempt $attempt)...")
                        val newServer = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
                            install(ContentNegotiation) {
                                gson()
                            }
                            install(WebSockets) {
                                pingPeriod = 30.seconds
                                timeout = 60.seconds
                                maxFrameSize = Long.MAX_VALUE
                                masking = false
                            }
                            routing {
                                get("/") {
                                    val content = safeReadAsset("web/index.html")
                                    if (content != null) {
                                        call.response.header(HttpHeaders.CacheControl, "no-cache, no-store, must-revalidate")
                                        call.respondBytes(content, ContentType.Text.Html)
                                    } else {
                                        call.respond(HttpStatusCode.NotFound)
                                    }
                                }
                                get("/style.css") {
                                    val content = safeReadAsset("web/style.css")
                                    if (content != null) {
                                        call.respondBytes(content, ContentType.Text.CSS)
                                    } else {
                                        call.respond(HttpStatusCode.NotFound)
                                    }
                                }
                                get("/script.js") {
                                    val content = safeReadAsset("web/script.js")
                                    if (content != null) {
                                        call.response.header(HttpHeaders.CacheControl, "no-cache, no-store, must-revalidate")
                                        call.respondBytes(content, ContentType.Application.JavaScript)
                                    } else {
                                        call.respond(HttpStatusCode.NotFound)
                                    }
                                }
                                get("/translations.json") {
                                    val content = safeReadAsset("web/translations.json")
                                    if (content != null) {
                                        call.respondBytes(content, ContentType.Application.Json)
                                    } else {
                                        call.respond(HttpStatusCode.NotFound)
                                    }
                                }
                                get("/favicon.png") {
                                    val content = safeReadAsset("web/favicon.png")
                                    if (content != null) {
                                        call.respondBytes(content, ContentType.Image.PNG)
                                    } else {
                                        call.respond(HttpStatusCode.NotFound)
                                    }
                                }

                                get("/api/config") {
                                    try {
                                        call.respond(mapOf("version" to BuildConfig.VERSION_NAME))
                                    } catch (e: Exception) {
                                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                                    }
                                }

                                get("/api/status") {
                                    val status = getStatusMap()
                                    if (status == null) {
                                        call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Player not available"))
                                    } else {
                                        call.respond(status)
                                    }
                                }

                                webSocket("/api/updates") {
                                    // Send current status and stations immediately on connect
                                    try {
                                        getStatusMap()?.let { send(Frame.Text(gson.toJson(mapOf("type" to "status", "data" to it)))) }
                                        val stations = getStationsList()
                                        send(Frame.Text(gson.toJson(mapOf("type" to "stations", "data" to stations))))
                                    } catch (_: Exception) { }

                                    val job = launch {
                                        statusUpdates.collect { update ->
                                            try {
                                                send(Frame.Text(gson.toJson(update)))
                                            } catch (_: Exception) { }
                                        }
                                    }
                                    try {
                                        for (frame in incoming) {
                                            // Just consume to keep connection alive
                                        }
                                    } catch (_: Exception) {
                                        Log.d("RemoteControlServer", "WebSocket client disconnected")
                                    } finally {
                                        job.cancel()
                                    }
                                }

                                get("/api/stations") {
                                    try {
                                        val stations = getStationsList()
                                        call.respond(stations)
                                    } catch (e: Exception) {
                                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                                    }
                                }

                                get("/api/image/{uuid}") {
                                    val uuid = call.parameters["uuid"]
                                    if (uuid != null) {
                                        try {
                                            val collection = withContext(Dispatchers.IO) {
                                                FileHelper.readCollection(context)
                                            }
                                            val station = CollectionHelper.getStation(collection, uuid)
                                            if (station.smallImage.isNotEmpty()) {
                                                val imageFile = File(context.getExternalFilesDir(""), FileHelper.determineDestinationFolderPath(com.michatec.radio.Keys.FILE_TYPE_IMAGE, uuid) + "/" + com.michatec.radio.Keys.STATION_IMAGE_FILE)
                                                if (imageFile.exists()) {
                                                    val lastModified = imageFile.lastModified()
                                                    val etag = "W/\"${lastModified}-${imageFile.length()}\""
                                                    
                                                    if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
                                                        call.respond(HttpStatusCode.NotModified)
                                                    } else {
                                                        call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
                                                        call.response.header(HttpHeaders.ETag, etag)
                                                        call.respondBytes(imageFile.readBytes(), ContentType.Image.JPEG)
                                                    }
                                                } else {
                                                    call.respond(HttpStatusCode.NotFound)
                                                }
                                            } else {
                                                call.respond(HttpStatusCode.NotFound)
                                            }
                                        } catch (_: Exception) {
                                            call.respond(HttpStatusCode.InternalServerError)
                                        }
                                    } else {
                                        call.respond(HttpStatusCode.BadRequest)
                                    }
                                }

                                post("/api/play/{uuid}") {
                                    val uuid = call.parameters["uuid"]
                                    if (uuid != null) {
                                        Log.i("RemoteControlServer", "Play request for: $uuid")
                                        withContext(Dispatchers.Main) {
                                            onPlayStation?.invoke(uuid)
                                        }
                                        call.respond(mapOf("status" to "ok"))
                                    }
                                }

                                post("/api/pause") {
                                    Log.i("RemoteControlServer", "Pause request")
                                    withContext(Dispatchers.Main) {
                                        onPause?.invoke()
                                    }
                                    call.respond(mapOf("status" to "ok"))
                                }

                                post("/api/resume") {
                                    Log.i("RemoteControlServer", "Resume request")
                                    withContext(Dispatchers.Main) {
                                        onResume?.invoke()
                                    }
                                    call.respond(mapOf("status" to "ok"))
                                }

                                post("/api/next") {
                                    Log.i("RemoteControlServer", "Next request")
                                    withContext(Dispatchers.Main) {
                                        onNext?.invoke()
                                    }
                                    call.respond(mapOf("status" to "ok"))
                                }

                                post("/api/prev") {
                                    Log.i("RemoteControlServer", "Prev request")
                                    withContext(Dispatchers.Main) {
                                        onPrev?.invoke()
                                    }
                                    call.respond(mapOf("status" to "ok"))
                                }
                            }
                        }
                        server = newServer
                        newServer.start(wait = false)
                        Log.i("RemoteControlServer", "Server successfully started on port 8080")
                        return@withLock
                    } catch (e: Exception) {
                        if (e is BindException || e.cause is BindException) {
                            Log.w("RemoteControlServer", "Port 8080 busy, retrying in 1s...")
                            delay(1000.milliseconds)
                            attempt++
                        } else {
                            Log.e("RemoteControlServer", "Critical server error", e)
                            onError?.invoke(e.localizedMessage ?: "Critical error")
                            break
                        }
                    }
                }
                Log.e("RemoteControlServer", "Failed to start after $maxAttempts attempts")
                onError?.invoke("Port 8080 is blocked by another process.")
            }
        }
    }

    fun stop() {
        val oldScope = serverScope
        oldScope.launch {
            lifecycleMutex.withLock {
                stopInternal()
            }
            oldScope.cancel()
        }
        serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    private fun stopInternal() {
        val s = server
        server = null
        if (s != null) {
            try {
                Log.i("RemoteControlServer", "Stopping server...")
                s.stop(500, 2000) // Higher grace period and timeout
                Log.i("RemoteControlServer", "Server stopped")
            } catch (e: Exception) {
                Log.e("RemoteControlServer", "Error stopping server", e)
            }
        }
    }
}

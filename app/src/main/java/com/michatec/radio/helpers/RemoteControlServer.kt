package com.michatec.radio.helpers

import android.content.Context
import android.util.Log
import androidx.media3.common.Player
import com.michatec.radio.BuildConfig
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.BindException
import kotlin.time.Duration.Companion.milliseconds

class RemoteControlServer(private val context: Context) {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var player: Player? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lifecycleMutex = Mutex()
    var onPlayStation: ((String) -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun setPlayer(player: Player?) {
        this.player = player
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
                                    val p = player
                                    if (p == null) {
                                        call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Player not available"))
                                    } else {
                                        try {
                                            val status = withContext(Dispatchers.Main) {
                                                val metadataHistory = PreferencesHelper.loadMetadataHistory()
                                                val currentTrack = if (metadataHistory.isNotEmpty()) metadataHistory.last() else ""
                                                val mediaId = p.currentMediaItem?.mediaId ?: PreferencesHelper.loadLastPlayedStationUuid()
                                                
                                                mapOf(
                                                    "isPlaying" to p.isPlaying,
                                                    "playbackState" to p.playbackState,
                                                    "currentStationUuid" to mediaId,
                                                    "metadata" to currentTrack
                                                )
                                            }
                                            call.respond(status)
                                        } catch (e: Exception) {
                                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                                        }
                                    }
                                }

                                get("/api/stations") {
                                    try {
                                        val collection = withContext(Dispatchers.IO) {
                                            FileHelper.readCollection(context)
                                        }
                                        val simplifiedStations = collection.stations.map {
                                            mapOf(
                                                "uuid" to it.uuid, 
                                                "name" to it.name,
                                                "hasImage" to it.smallImage.isNotEmpty()
                                            )
                                        }
                                        call.respond(simplifiedStations)
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
                                                    call.respondBytes(imageFile.readBytes(), ContentType.Image.JPEG)
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
        serverScope.launch {
            lifecycleMutex.withLock {
                stopInternal()
            }
        }
    }

    private fun stopInternal() {
        val s = server
        server = null
        if (s != null) {
            try {
                Log.i("RemoteControlServer", "Stopping server...")
                s.stop(50, 100)
                Log.i("RemoteControlServer", "Server stopped")
            } catch (e: Exception) {
                Log.e("RemoteControlServer", "Error stopping server", e)
            }
        }
    }
}

package com.michatec.radio.helpers

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.gson.Gson
import com.michatec.radio.remote.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.BindException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RemoteControlServer(private val context: Context) {

    companion object {
        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
        private val lifecycleMutex = Mutex()
    }

    private var player: Player? = null
    private var serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val _statusUpdates = MutableSharedFlow<WebSocketMessage>(extraBufferCapacity = 1)
    private val statusUpdates = _statusUpdates.asSharedFlow()

    var onPlayStation: ((String) -> Unit)? = null
    var onGetCollection: (() -> com.michatec.radio.core.Collection)? = null
    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onAuthFailed: ((String) -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = broadcastStatus()
        override fun onPlaybackStateChanged(playbackState: Int) = broadcastStatus()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = broadcastStatus()
        @UnstableApi override fun onMetadata(metadata: Metadata) = broadcastStatus()
    }

    private suspend fun getStationsList(): List<StationDto> {
        val collection = onGetCollection?.invoke() ?: withContext(Dispatchers.IO) {
            FileHelper.readCollection(context)
        }
        return collection.stations.map {
            val imageFile = FileHelper.getStationImageFile(context, it.uuid)
            StationDto(
                uuid = it.uuid,
                name = it.name,
                hasImage = it.smallImage.isNotEmpty(),
                starred = it.starred,
                lastModified = if (imageFile.exists()) imageFile.lastModified() else 0L,
            )
        }
    }

    fun notifyCollectionChanged() {
        serverScope.launch {
            try {
                _statusUpdates.emit(WebSocketMessage(RemoteConstants.WebSocket.TYPE_STATIONS, getStationsList()))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("RemoteControlServer", "Error broadcasting stations", e)
            }
        }
    }

    private fun broadcastStatus() {
        serverScope.launch {
            getStatusMap()?.let {
                _statusUpdates.emit(WebSocketMessage(RemoteConstants.WebSocket.TYPE_STATUS, it))
            }
        }
    }

    private suspend fun getStatusMap(): StatusResponse? {
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

                StatusResponse(
                    isPlaying = p.isPlaying,
                    playWhenReady = p.playWhenReady,
                    playbackState = p.playbackState,
                    currentStationUuid = mediaId,
                    metadata = currentTrack,
                    starred = station.starred,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("RemoteControlServer", "Error getting status map", e)
            null
        }
    }

    private suspend fun runOnMain(action: () -> Unit) = withContext(Dispatchers.Main) {
        action()
    }

    fun setPlayer(player: Player?) {
        this.player?.removeListener(playerListener)
        this.player = player
        this.player?.addListener(playerListener)
    }

    fun start() {
        serverScope.launch {
            lifecycleMutex.withLock {
                if (server != null) return@withLock

                var attempt = 1
                val maxAttempts = 5
                
                while (attempt <= maxAttempts) {
                    try {
                        val routes = RemoteRoutes(
                            context = context,
                            gson = gson,
                            getStatus = { getStatusMap() },
                            getStations = { getStationsList() },
                            statusUpdates = statusUpdates
                        ) { action, data ->
                            runOnMain {
                                when (action) {
                                    "play" -> onPlayStation?.invoke(data!!)
                                    "pause" -> onPause?.invoke()
                                    "resume" -> {
                                        if (player?.playbackState == Player.STATE_IDLE) {
                                            onPlayStation?.invoke(player?.currentMediaItem?.mediaId ?: PreferencesHelper.loadLastPlayedStationUuid())
                                        } else onResume?.invoke()
                                    }
                                    "next" -> onNext?.invoke()
                                    "prev" -> onPrev?.invoke()
                                }
                            }
                        }

                        server = embeddedServer(Netty, port = RemoteConstants.PORT, host = RemoteConstants.HOST) {
                            install(ContentNegotiation) { gson() }
                            install(WebSockets) {
                                pingPeriod = 30.seconds
                                timeout = 60.seconds
                            }
                            install(RemoteAuthPlugin) {
                                onAuthFailed = { ip ->
                                    serverScope.launch(Dispatchers.Main) { this@RemoteControlServer.onAuthFailed?.invoke(ip) }
                                }
                            }
                            routing {
                                with(routes) { installRoutes() }
                            }
                        }.apply { start(wait = false) }
                        
                        Log.i("RemoteControlServer", "Server successfully started on port ${RemoteConstants.PORT}")
                        return@withLock
                    } catch (e: Exception) {
                        if ((e is BindException) || (e.cause is BindException)) {
                            Log.w("RemoteControlServer", "Port busy, retrying...")
                            delay(1000.milliseconds)
                            attempt++
                        } else {
                            Log.e("RemoteControlServer", "Critical server error", e)
                            runOnMain { onError?.invoke(e.localizedMessage ?: "Critical error") }
                            break
                        }
                    }
                }
                runOnMain { onError?.invoke("Port 8080 is blocked.") }
            }
        }
    }

    fun stop() {
        val oldScope = serverScope
        oldScope.launch {
            lifecycleMutex.withLock {
                server?.let {
                    Log.i("RemoteControlServer", "Stopping server...")
                    it.stop(500, 2000)
                    server = null
                }
            }
            oldScope.cancel()
        }
        serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}

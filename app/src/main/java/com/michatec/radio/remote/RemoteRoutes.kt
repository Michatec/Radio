package com.michatec.radio.remote

import android.content.Context
import com.google.gson.Gson
import com.michatec.radio.BuildConfig
import com.michatec.radio.helpers.CollectionHelper
import com.michatec.radio.helpers.FileHelper
import com.michatec.radio.remote.RemoteConstants.Routes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RemoteRoutes(
    private val context: Context,
    private val gson: Gson,
    private val getStatus: suspend () -> StatusResponse?,
    private val getStations: suspend () -> List<StationDto>,
    private val statusUpdates: SharedFlow<WebSocketMessage>,
    private val onAction: suspend (String, String?) -> Unit,
) {

    fun Routing.installRoutes() {
        // Static Content
        get(Routes.ROOT) { call.respondAsset(context, "web/index.html", ContentType.Text.Html) }
        get(Routes.STYLE) { call.respondAsset(context, "web/style.css", ContentType.Text.CSS) }
        get(Routes.SCRIPT) { call.respondAsset(context, "web/script.js", ContentType.Application.JavaScript) }
        get(Routes.TRANSLATIONS) { call.respondAsset(context, "web/translations.json", ContentType.Application.Json) }
        get(Routes.FAVICON) { call.respondAsset(context, "web/favicon.png", ContentType.Image.PNG) }

        // API
        get(Routes.API_CONFIG) {
            call.respond(ConfigResponse(BuildConfig.VERSION_NAME))
        }

        get(Routes.API_STATUS) {
            val status = getStatus()
            if (status == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Player not available"))
            } else {
                call.respond(status)
            }
        }

        get(Routes.API_STATIONS) {
            call.respond(getStations())
        }

        get(Routes.API_IMAGE) {
            val uuid = call.parameters["uuid"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            try {
                val collection = withContext(Dispatchers.IO) { FileHelper.readCollection(context) }
                val station = CollectionHelper.getStation(collection, uuid)
                
                if (station.smallImage.isNotEmpty()) {
                    val imageFile = File(context.getExternalFilesDir(""), FileHelper.determineDestinationFolderPath(com.michatec.radio.Keys.FILE_TYPE_IMAGE, uuid) + "/" + com.michatec.radio.Keys.STATION_IMAGE_FILE)
                    if (imageFile.exists()) {
                        val lastModified = imageFile.lastModified()
                        val etag = "W/\"$lastModified-${imageFile.length()}\""
                        
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
        }

        // WebSockets
        webSocket(Routes.API_UPDATES) {
            try {
                getStatus()?.let { send(Frame.Text(gson.toJson(WebSocketMessage(RemoteConstants.WebSocket.TYPE_STATUS, it)))) }
                send(Frame.Text(gson.toJson(WebSocketMessage(RemoteConstants.WebSocket.TYPE_STATIONS, getStations()))))
            } catch (_: Exception) { }

            val job = launch {
                statusUpdates.collect { update ->
                    try {
                        send(Frame.Text(gson.toJson(update)))
                    } catch (_: Exception) { }
                }
            }
            try {
                for (frame in incoming) { /* Keep alive */ frame.takeIf { it is Frame.Text } ?: continue }
            } catch (_: Exception) {
            } finally {
                job.cancel()
            }
        }

        // Control Actions
        post(Routes.API_PLAY) {
            val uuid = call.parameters["uuid"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            onAction("play", uuid)
            call.respond(GenericResponse())
        }

        post(Routes.API_PAUSE) {
            onAction("pause", null)
            call.respond(GenericResponse())
        }

        post(Routes.API_RESUME) {
            onAction("resume", null)
            call.respond(GenericResponse())
        }

        post(Routes.API_NEXT) {
            onAction("next", null)
            call.respond(GenericResponse())
        }

        post(Routes.API_PREV) {
            onAction("prev", null)
            call.respond(GenericResponse())
        }
    }
}

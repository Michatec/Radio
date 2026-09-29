package com.michatec.radio.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.michatec.radio.BuildConfig
import com.michatec.radio.helpers.CollectionHelper
import com.michatec.radio.helpers.FileHelper
import com.michatec.radio.helpers.PreferencesHelper
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
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

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
        get(Routes.STYLE) { call.respondAsset(context, "web/css/style.css", ContentType.Text.CSS) }
        get(Routes.SCRIPT) { call.respondAsset(context, "web/js/script.js", ContentType.Application.JavaScript) }
        get(Routes.API_SCRIPT) { call.respondAsset(context, "web/js/api.js", ContentType.Application.JavaScript) }
        get(Routes.UI_SCRIPT) { call.respondAsset(context, "web/js/ui.js", ContentType.Application.JavaScript) }
        get(Routes.TRANSLATIONS) { call.respondAsset(context, "web/translations.json", ContentType.Application.Json) }
        get(Routes.FAVICON) { call.respondAsset(context, "web/favicon.png", ContentType.Image.PNG) }

        // API
        get(Routes.API_CONFIG) {
            call.respond(ConfigResponse(BuildConfig.VERSION_NAME))
        }

        post(Routes.API_PAIR_START) {
            val clientId = call.request.queryParameters["clientId"]
            val clientKey = call.request.headers[RemoteConstants.Auth.HEADER_PAIR_KEY]
            val code = PairingManager.startSession(clientId, clientKey)
            if (code == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid pairing request"))
            } else {
                call.respond(PairStartResponse(code, RemoteConstants.Pairing.SESSION_TTL_SECONDS))
            }
        }

        get(Routes.API_QR) {
            val clientId = call.parameters["clientId"]
            val clientKey = call.request.headers[RemoteConstants.Auth.HEADER_PAIR_KEY]
            val code = PairingManager.getSessionCode(clientId, clientKey)
            if (code == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("No pairing session"))
                return@get
            }
            val qrData = "radio-pair:$clientId:$code"
            val qrBytes = generateQRCodeBytes(qrData)
            call.respondBytes(qrBytes, ContentType.Image.PNG)
        }

        get(Routes.API_PAIR_STATUS) {
            val clientId = call.request.queryParameters["clientId"]
            val clientKey = call.request.headers[RemoteConstants.Auth.HEADER_PAIR_KEY]
            val authEnabled = PreferencesHelper.loadRemoteControlAuthEnabled()
            val token = if (authEnabled) PairingManager.consumeToken(clientId, clientKey) else null
            if (token != null) {
                call.respond(mapOf("paired" to true, "token" to token))
            } else {
                call.respond(mapOf("paired" to false))
            }
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

        get(Routes.API_STREAM) {
            val status = getStatus()
            val uuid = status?.currentStationUuid
            if (uuid.isNullOrEmpty()) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("No station playing"))
                return@get
            }
            val collection = withContext(Dispatchers.IO) { FileHelper.readCollection(context) }
            val station = try {
                CollectionHelper.getStation(collection, uuid)
            } catch (_: Exception) {
                null
            }
            val streamUri = station?.streamUris?.getOrNull(station.stream)
            if (streamUri.isNullOrEmpty()) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Stream URL not found"))
                return@get
            }
            call.respond(GenericResponse(status = streamUri))
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

    private fun generateQRCodeBytes(text: String, width: Int = 300, height: Int = 300): ByteArray {
        val hints = hashMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 1
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints)
        val bmp = createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}

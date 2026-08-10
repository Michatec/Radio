package com.michatec.radio.remote

import android.content.Context
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import android.util.Log

suspend fun ApplicationCall.respondAsset(context: Context, path: String, contentType: ContentType) {
    try {
        val bytes = context.assets.open(path).use { it.readBytes() }
        if (contentType == ContentType.Text.Html) {
            response.header(HttpHeaders.CacheControl, "no-cache, no-store, must-revalidate")
        }
        respondBytes(bytes, contentType)
    } catch (e: Exception) {
        Log.e("RemoteControlServer", "Failed to read asset: $path", e)
        respond(HttpStatusCode.NotFound)
    }
}

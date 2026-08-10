package com.michatec.radio.remote

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import com.michatec.radio.helpers.PreferencesHelper

class AuthConfiguration {
    var onAuthFailed: ((String) -> Unit)? = null
}

val RemoteAuthPlugin = createApplicationPlugin(name = "RemoteAuthPlugin", createConfiguration = ::AuthConfiguration) {
    val failedAttemptsMap = ConcurrentHashMap<String, Int>()
    val onAuthFailed = pluginConfig.onAuthFailed

    onCall { call ->
        val authEnabled = PreferencesHelper.loadRemoteControlAuthEnabled()
        if (!authEnabled) return@onCall

        val requestPath = call.request.path()
        val isStatic = (requestPath == RemoteConstants.Routes.ROOT) || 
                      (requestPath == RemoteConstants.Routes.STYLE) || 
                      (requestPath == RemoteConstants.Routes.SCRIPT) || 
                      (requestPath == RemoteConstants.Routes.FAVICON) || 
                      (requestPath == RemoteConstants.Routes.TRANSLATIONS)
        
        if (isStatic) return@onCall

        val secret = PreferencesHelper.loadRemoteControlSecretToken()
        val providedKey = call.request.headers[RemoteConstants.Auth.HEADER_KEY] 
                          ?: call.request.queryParameters[RemoteConstants.Auth.QUERY_PARAM_TOKEN]
        val remoteHost = call.request.local.remoteHost

        if (providedKey != secret) {
            val attempts = (failedAttemptsMap[remoteHost] ?: 0) + 1
            failedAttemptsMap[remoteHost] = attempts
            
            if (attempts >= RemoteConstants.Auth.MAX_FAILED_ATTEMPTS) {
                onAuthFailed?.invoke(remoteHost)
            }
            
            delay(2000.milliseconds)
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
        } else {
            failedAttemptsMap.remove(remoteHost)
        }
    }
}

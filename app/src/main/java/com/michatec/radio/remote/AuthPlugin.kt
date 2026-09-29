package com.michatec.radio.remote

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import com.michatec.radio.helpers.PreferencesHelper

class AuthConfiguration {
    var onAuthFailed: ((String) -> Unit)? = null
}

val RemoteAuthPlugin = createApplicationPlugin(name = "RemoteAuthPlugin", createConfiguration = ::AuthConfiguration) {
    val failedAttemptsMap = ConcurrentHashMap<String, FailedAttempts>()
    val onAuthFailed = pluginConfig.onAuthFailed

    onCall { call ->
        val authEnabled = PreferencesHelper.loadRemoteControlAuthEnabled()
        if (!authEnabled) return@onCall

        val requestPath = call.request.path()
        val isStatic = (requestPath == RemoteConstants.Routes.ROOT) ||
                      (requestPath == RemoteConstants.Routes.STYLE) ||
                      (requestPath == RemoteConstants.Routes.SCRIPT) ||
                      (requestPath == RemoteConstants.Routes.API_SCRIPT) ||
                      (requestPath == RemoteConstants.Routes.UI_SCRIPT) ||
                      (requestPath == RemoteConstants.Routes.FAVICON) ||
                      (requestPath == RemoteConstants.Routes.TRANSLATIONS) ||
                      (requestPath == RemoteConstants.Routes.API_QR) ||
                      (requestPath == RemoteConstants.Routes.API_PAIR_START) ||
                      (requestPath == RemoteConstants.Routes.API_PAIR_STATUS)
        
        if (isStatic) return@onCall

        val secret = PreferencesHelper.loadRemoteControlSecretToken()
        val providedKey = call.request.headers[RemoteConstants.Auth.HEADER_KEY] 
                          ?: call.request.queryParameters[RemoteConstants.Auth.QUERY_PARAM_TOKEN]
        val remoteHost = call.request.local.remoteHost
        val now = System.currentTimeMillis()

        purgeIdleHosts(failedAttemptsMap, now)
        val attempts = failedAttemptsMap.computeIfAbsent(remoteHost) { FailedAttempts() }

        if (attempts.blockedUntil > now) {
            call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("Too many failed attempts"))
            return@onCall
        }

        if (!constantTimeEquals(secret, providedKey)) {
            attempts.failedAttempts++
            if (attempts.failedAttempts >= RemoteConstants.Auth.MAX_FAILED_ATTEMPTS) {
                val overAttempts = attempts.failedAttempts - RemoteConstants.Auth.MAX_FAILED_ATTEMPTS
                val factor = min(1L shl min(overAttempts, 8), RemoteConstants.Auth.MAX_LOCKOUT_FACTOR.toLong())
                attempts.blockedUntil = now + RemoteConstants.Auth.LOCKOUT_COOLDOWN_MS * factor
                onAuthFailed?.invoke(remoteHost)
            }

            delay(RemoteConstants.Auth.FAILURE_DELAY_MS.milliseconds)
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
        } else {
            failedAttemptsMap.remove(remoteHost)
        }
    }
}

private class FailedAttempts {
    var failedAttempts: Int = 0
    var blockedUntil: Long = 0
}

private fun purgeIdleHosts(failedAttemptsMap: ConcurrentHashMap<String, FailedAttempts>, now: Long) {
    if (failedAttemptsMap.size <= RemoteConstants.Auth.MAX_TRACKED_HOSTS) return
    failedAttemptsMap.entries.removeIf { it.value.blockedUntil <= now }
}

private fun constantTimeEquals(secret: String, providedKey: String?): Boolean {
    if (providedKey == null) return false
    return MessageDigest.isEqual(secret.toByteArray(Charsets.UTF_8), providedKey.toByteArray(Charsets.UTF_8))
}

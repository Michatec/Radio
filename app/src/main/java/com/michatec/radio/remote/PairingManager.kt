package com.michatec.radio.remote

import android.util.Log
import com.michatec.radio.helpers.PreferencesHelper
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

object PairingManager {

    /* Length of the pairing code in hex characters */
    private const val CODE_LENGTH = 32

    /* Limits for the values provided by the client */
    private const val CLIENT_ID_MAX_LENGTH = 128
    private const val CLIENT_KEY_MIN_LENGTH = 32
    private const val CLIENT_KEY_MAX_LENGTH = 256

    /* A pairing session is dropped after this time */
    private const val SESSION_TTL_MS = 15 * 60 * 1000L

    /* An approved session stays available for this time */
    private const val APPROVED_TTL_MS = 5 * 60 * 1000L

    /* Upper bound of parallel sessions to keep the store small */
    private const val MAX_SESSIONS = 32

    /* Define log tag */
    private const val TAG: String = "PairingManager"

    /* Single pairing session */
    private class Session(
        val clientKey: String,
        val code: String,
        val createdAt: Long
    ) {
        @Volatile var approvedAt: Long = 0
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val secureRandom = SecureRandom()


    /* Checks if a client id has an acceptable format */
    fun isValidClientId(clientId: String?): Boolean {
        if (clientId.isNullOrEmpty() || clientId.length > CLIENT_ID_MAX_LENGTH) return false
        return clientId.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
    }


    /* Checks if a client key has an acceptable format */
    fun isValidClientKey(clientKey: String?): Boolean {
        if (clientKey == null) return false
        if (clientKey.length !in CLIENT_KEY_MIN_LENGTH..CLIENT_KEY_MAX_LENGTH) return false
        return clientKey.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }


    /* Creates (or refreshes) the pairing session of a client and returns its pairing code */
    fun startSession(clientId: String?, clientKey: String?): String? {
        if (!isValidClientId(clientId) || !isValidClientKey(clientKey)) return null
        val id = clientId!!
        purgeExpired()
        val existing = sessions[id]
        if (existing != null && !isExpired(existing) && constantTimeEquals(existing.clientKey, clientKey)) {
            return existing.code
        }
        if (sessions.size >= MAX_SESSIONS) {
            sessions.entries.minByOrNull { it.value.createdAt }?.let { sessions.remove(it.key) }
        }
        val code = generateCode()
        sessions[id] = Session(clientKey!!, code, System.currentTimeMillis())
        Log.i(TAG, "Pairing session started for client $id")
        return code
    }


    /* Returns the pairing code of a session, but only for the owner of the session key */
    fun getSessionCode(clientId: String?, clientKey: String?): String? {
        if (clientId == null || clientKey == null) return null
        val session = sessions[clientId] ?: return null
        if (isExpired(session) || !constantTimeEquals(session.clientKey, clientKey)) return null
        return session.code
    }


    /* Called by the app after the user scanned a QR code, returns true if the code was valid */
    fun approveClient(code: String?): Boolean {
        if (code.isNullOrEmpty()) return false
        val session = sessions.values.firstOrNull { !isExpired(it) && constantTimeEquals(it.code, code) }
        if (session == null) {
            Log.w(TAG, "Scanned pairing code does not match any open session")
            return false
        }
        session.approvedAt = System.currentTimeMillis()
        Log.i(TAG, "Pairing session approved, the client can now collect its token")
        return true
    }


    /* Returns the secret for an approved session, the session is dropped afterwards */
    fun consumeToken(clientId: String?, clientKey: String?): String? {
        if (clientId == null || clientKey == null) return null
        val session = sessions[clientId] ?: return null
        if (isExpired(session) || !constantTimeEquals(session.clientKey, clientKey)) return null
        val approvedAt = session.approvedAt
        if (approvedAt == 0L) return null
        sessions.remove(clientId)
        if (System.currentTimeMillis() - approvedAt > APPROVED_TTL_MS) return null
        return PreferencesHelper.loadRemoteControlSecretToken()
    }


    /* Drops all pairing sessions, e.g. when the secret is regenerated or auth is disabled */
    fun clear() {
        sessions.clear()
    }


    /* Checks if a session is older than the allowed lifetime */
    private fun isExpired(session: Session): Boolean {
        return System.currentTimeMillis() - session.createdAt > SESSION_TTL_MS
    }


    /* Removes all outdated sessions */
    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { now - it.value.createdAt > SESSION_TTL_MS }
    }


    /* Generates a cryptographically secure pairing code */
    private fun generateCode(): String {
        val bytes = ByteArray(CODE_LENGTH / 2)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }


    /* Compares two secrets without leaking their content through timing */
    private fun constantTimeEquals(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }
}

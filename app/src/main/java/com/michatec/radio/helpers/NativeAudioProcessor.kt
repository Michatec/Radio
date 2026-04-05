package com.michatec.radio.helpers

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class NativeAudioProcessor : BaseAudioProcessor() {

    companion object {
        init {
            System.loadLibrary("radio")
        }
    }

    // JNI Methods
    private external fun setDrcEnabled(enabled: Boolean)
    private external fun setReverbMix(mix: Float)
    private external fun setEqBand(band: Int, gainDb: Float)
    private external fun setBassBoost(gainDb: Float)
    private external fun processAudio(data: ShortArray, size: Int)

    // Public API
    fun enableDrc(enabled: Boolean) = setDrcEnabled(enabled)
    fun setReverb(mix: Float) = setReverbMix(mix)
    fun setEq(band: Int, gainDb: Float) = setEqBand(band, gainDb)
    fun enableBassBoost(gainDb: Float) = setBassBoost(gainDb)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val shortArraySize = remaining / 2
        val shortArray = ShortArray(shortArraySize)

        // Input-Daten lesen
        inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer().get(shortArray)

        // Native Verarbeitung
        processAudio(shortArray, shortArraySize)

        // Buffer der Basisklasse anfordern und befüllen
        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.asShortBuffer().put(shortArray)
        outputBuffer.limit(remaining) // Markiert das Ende der geschriebenen Daten

        // Input-Buffer als verarbeitet markieren
        inputBuffer.position(inputBuffer.limit())
    }
}
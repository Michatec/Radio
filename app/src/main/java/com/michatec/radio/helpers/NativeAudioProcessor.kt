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

    // ===== JNI =====
    private external fun setDrcEnabled(enabled: Boolean)
    private external fun setReverbMix(mix: Float)
    private external fun setEqBand(band: Int, gainDb: Float)
    private external fun setBassBoost(gainDb: Float)
    private external fun setStereoWidth(width: Float)
    private external fun processAudioDirect(buf: ByteBuffer, size: Int)
    private external fun getFftData(): FloatArray

    // ===== API =====
    fun enableDrc(enabled: Boolean) = setDrcEnabled(enabled)
    fun setReverb(mix: Float) = setReverbMix(mix)
    fun setEq(band: Int, gainDb: Float) = setEqBand(band, gainDb)
    fun setEqAll(gains: FloatArray) {
        gains.forEachIndexed { i, g -> setEq(i, g) }
    }
    fun enableBassBoost(gainDb: Float) = setBassBoost(gainDb)
    fun setWidth(width: Float) = setStereoWidth(width)

    @Suppress("unused")
    fun getVisualizer(): FloatArray {
        val raw = getFftData()
        val out = FloatArray(raw.size)
        for (i in raw.indices) out[i] = kotlin.math.log10(1f + raw[i])
        return out
    }

    // ===== AudioProcessor Overrides =====
    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT)
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        // Direct ByteBuffer -> JNI
        inputBuffer.order(ByteOrder.nativeOrder())
        processAudioDirect(inputBuffer, size)

        // Replace output buffer
        val out = replaceOutputBuffer(size)
        out.order(ByteOrder.nativeOrder())

        // Mark as processed and copy to output
        val currentPos = inputBuffer.position()
        out.put(inputBuffer)
        inputBuffer.position(currentPos + size)

        out.flip()
    }

    // ===== Presets =====
    fun setPresetRock() {
        enableDrc(true)
        setReverb(0.2f)
        setWidth(1.1f)
        setEqAll(floatArrayOf(2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 2f, 3f))
        enableBassBoost(1.5f)
    }

    fun setPresetPop() {
        enableDrc(true)
        setReverb(0.15f)
        setWidth(1.05f)
        setEqAll(floatArrayOf(1f, 1f, 0f, 0f, 0f, 0f, 1f, 2f, 2f, 1f))
        enableBassBoost(1.0f)
    }

    fun setPresetJazz() {
        enableDrc(false)
        setReverb(0.15f)
        setWidth(1.0f)
        setEqAll(floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f))
        enableBassBoost(0.5f)
    }

    fun setPresetFlat() {
        enableDrc(false)
        setReverb(0f)
        setWidth(1f)
        setEqAll(FloatArray(10))
        enableBassBoost(0f)
    }
}
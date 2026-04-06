package com.michatec.radio.helpers

import android.util.Log
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
        private const val TAG = "NativeAudioProcessor"
        init {
            try {
                System.loadLibrary("dsp")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dsp library", e)
            }
        }
    }

    private var directBuffer: ByteBuffer? = null

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

    fun getVisualizer(): FloatArray {
        val raw = getFftData()
        val out = FloatArray(raw.size)
        for (i in raw.indices) out[i] = kotlin.math.log10(1f + raw[i])
        return out
    }

    // ===== AudioProcessor Overrides =====
    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Always try to support the input format if it is PCM 16-bit
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            Log.e(TAG, "Unsupported encoding: ${inputAudioFormat.encoding}")
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        // Always ensure we have a direct buffer for JNI
        if (directBuffer == null || directBuffer!!.capacity() < size) {
            directBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        }
        
        directBuffer!!.clear()
        inputBuffer.position()
        directBuffer!!.put(inputBuffer)
        
        directBuffer!!.flip()
        
        // Process audio in JNI
        processAudioDirect(directBuffer!!, size)

        // Copy processed data back to output
        val out = replaceOutputBuffer(size)
        out.order(ByteOrder.nativeOrder())
        
        directBuffer!!.position(0)
        out.put(directBuffer!!)
        out.flip()

    }

    override fun onReset() {
        super.onReset()
        directBuffer = null
    }

    // ===== Presets =====
    fun setPresetRock() {
        enableDrc(true)
        setReverb(0.10f)
        setWidth(1.1f)
        setEqAll(floatArrayOf(2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 2f, 3f))
        enableBassBoost(0.6f)
    }

    fun setPresetPop() {
        enableDrc(true)
        setReverb(0.15f)
        setWidth(1.05f)
        setEqAll(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 2f, 2f, 1f))
        enableBassBoost(0.5f)
    }

    fun setPresetJazz() {
        enableDrc(false)
        setReverb(0.15f)
        setWidth(1.0f)
        setEqAll(floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f))
        enableBassBoost(0.2f)
    }

    fun setPresetFlat() {
        enableDrc(false)
        setReverb(0f)
        setWidth(1f)
        setEqAll(FloatArray(10))
        enableBassBoost(0f)
    }
}
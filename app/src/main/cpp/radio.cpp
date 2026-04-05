#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <algorithm>
#include <android/log.h>

// --- DSP Classes ---

/**
 * Biquad Filter for EQ and Shelving
 */
class Biquad {
public:
    float a0 = 1.0f, a1 = 0.0f, a2 = 0.0f, b1 = 0.0f, b2 = 0.0f;
    float z1 = 0.0f, z2 = 0.0f;

    void setPeakingEQ(float sampleRate, float freq, float gainDb, float bandwidth) {
        float a = powf(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * static_cast<float>(M_PI) * freq / sampleRate;
        float alpha = sinf(w0) * sinhf(logf(2.0f) / 2.0f * bandwidth * w0 / sinf(w0));

        float b0 = 1.0f + alpha * a;
        a1 = -2.0f * cosf(w0);
        a2 = 1.0f - alpha * a;
        float b0_inv = 1.0f / (1.0f + alpha / a);
        b1 = -2.0f * cosf(w0) * b0_inv;
        b2 = (1.0f - alpha / a) * b0_inv;
        a0 = b0 * b0_inv;
        a1 *= b0_inv;
        a2 *= b0_inv;
    }

    void setLowShelf(float sampleRate, float frequency, float gainDb, float q) {
        float a = powf(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * static_cast<float>(M_PI) * frequency / sampleRate;
        float alpha = sinf(w0) / 2.0f * sqrtf((a + 1.0f / a) * (1.0f / q - 1.0f) + 2.0f);
        float cosW0 = cosf(w0);

        float b0 = a * ((a + 1.0f) - (a - 1.0f) * cosW0 + 2.0f * sqrtf(a) * alpha);
        a1 = 2.0f * a * ((a - 1.0f) - (a + 1.0f) * cosW0);
        a2 = a * ((a + 1.0f) - (a - 1.0f) * cosW0 - 2.0f * sqrtf(a) * alpha);
        float b0_inv = 1.0f / ((a + 1.0f) + (a - 1.0f) * cosW0 + 2.0f * sqrtf(a) * alpha);
        b1 = -2.0f * ((a - 1.0f) + (a + 1.0f) * cosW0) * b0_inv;
        b2 = ((a + 1.0f) + (a - 1.0f) * cosW0 - 2.0f * sqrtf(a) * alpha) * b0_inv;
        a0 = b0 * b0_inv;
        a1 *= b0_inv;
        a2 *= b0_inv;
    }

    float process(float in) {
        float out = in * a0 + z1;
        z1 = in * a1 + z2 - b1 * out;
        z2 = in * a2 - b2 * out;
        return out;
    }
};

/**
 * Dynamic Range Compressor
 */
class Compressor {
public:
    float threshold = 0.3f;
    float ratio = 4.0f;
    float attack = 0.01f;
    float release = 0.2f;
    float sampleRate = 44100.0f;
    float envelope = 0.0f;

    void process(float* buffer, int size) {
        float attackCoef = expf(-1.0f / (attack * sampleRate));
        float releaseCoef = expf(-1.0f / (release * sampleRate));

        for (int i = 0; i < size; ++i) {
            float absInput = std::abs(buffer[i]);
            if (absInput > envelope)
                envelope = attackCoef * (envelope - absInput) + absInput;
            else
                envelope = releaseCoef * (envelope - absInput) + absInput;

            if (envelope > threshold) {
                float gainReduction = threshold + (envelope - threshold) / ratio;
                buffer[i] *= (gainReduction / envelope);
            }
        }
    }
};

/**
 * Simple Reverb (Comb Filter based)
 */
class Reverb {
public:
    std::vector<float> delayLine;
    int pos = 0;
    float feedback = 0.4f;
    float mix = 0.0f;

    Reverb() { delayLine.resize(4410, 0.0f); } // ~100ms

    float process(float in) {
        float delayed = delayLine[static_cast<size_t>(pos)];
        delayLine[static_cast<size_t>(pos)] = in + delayed * feedback;
        pos = (pos + 1) % static_cast<int>(delayLine.size());
        return in + delayed * mix;
    }
};

// --- Global Engine State ---
Compressor gCompressor;
Reverb gReverb;
std::vector<Biquad> gEqBands(10);
Biquad gBassBoost;

bool gDrcEnabled = false;
bool gReverbEnabled = false;
bool gEqEnabled = false;
bool gBassBoostEnabled = false;

extern "C" {

JNIEXPORT void JNICALL
Java_com_michatec_radio_helpers_NativeAudioProcessor_setDrcEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    gDrcEnabled = enabled;
}

JNIEXPORT void JNICALL
Java_com_michatec_radio_helpers_NativeAudioProcessor_setReverbMix(JNIEnv *env, jobject thiz, jfloat mix) {
    gReverb.mix = mix;
    gReverbEnabled = (mix > 0.01f);
}

JNIEXPORT void JNICALL
Java_com_michatec_radio_helpers_NativeAudioProcessor_setEqBand(JNIEnv *env, jobject thiz, jint band, jfloat gainDb) {
    float freqs[] = {31.25f, 62.5f, 125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f};
    if (band >= 0 && band < 10) {
        gEqBands[static_cast<size_t>(band)].setPeakingEQ(44100.0f, freqs[band], gainDb, 1.0f);
        gEqEnabled = true;
    }
}

JNIEXPORT void JNICALL
Java_com_michatec_radio_helpers_NativeAudioProcessor_setBassBoost(JNIEnv *env, jobject thiz, jfloat gainDb) {
    if (gainDb > 0.0f) {
        gBassBoost.setLowShelf(44100.0f, 150.0f, gainDb, 0.707f);
        gBassBoostEnabled = true;
    } else {
        gBassBoostEnabled = false;
    }
}

JNIEXPORT void JNICALL
Java_com_michatec_radio_helpers_NativeAudioProcessor_processAudio(JNIEnv *env, jobject thiz, jshortArray data, jint size) {
    jshort *buffer = env->GetShortArrayElements(data, nullptr);
    if (!buffer) return;

    std::vector<float> floatBuf(static_cast<size_t>(size));
    for (int i = 0; i < size; ++i) floatBuf[static_cast<size_t>(i)] = static_cast<float>(buffer[i]) / 32768.0f;

    // Apply EQ
    if (gEqEnabled) {
        for (auto &band : gEqBands) {
            for (int i = 0; i < size; ++i) floatBuf[static_cast<size_t>(i)] = band.process(floatBuf[static_cast<size_t>(i)]);
        }
    }

    // Apply Bass Boost
    if (gBassBoostEnabled) {
        for (int i = 0; i < size; ++i) floatBuf[static_cast<size_t>(i)] = gBassBoost.process(floatBuf[static_cast<size_t>(i)]);
    }

    // Apply Reverb
    if (gReverbEnabled) {
        for (int i = 0; i < size; ++i) floatBuf[static_cast<size_t>(i)] = gReverb.process(floatBuf[static_cast<size_t>(i)]);
    }

    // Apply Compressor (at the end to prevent clipping)
    if (gDrcEnabled) {
        gCompressor.process(floatBuf.data(), size);
    }

    // Back to short
    for (int i = 0; i < size; ++i) {
        float out = std::max(-1.0f, std::min(1.0f, floatBuf[static_cast<size_t>(i)]));
        buffer[i] = static_cast<jshort>(out * 32767.0f);
    }

    env->ReleaseShortArrayElements(data, buffer, 0);
}

} // extern "C"
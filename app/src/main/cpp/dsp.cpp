#include <jni.h>
#include <vector>
#include <cmath>
#include <complex>
#include <array>
#include <atomic>

#if defined(__ARM_NEON)
#include <arm_neon.h>
#endif

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static std::atomic<float> gSampleRate(44100.0f);
static constexpr int FFT_SIZE = 2048;
static constexpr int NUM_EQ_BANDS = 10;
static constexpr float INV_32768 = 1.0f / 32768.0f;
static constexpr float SQRT_2_INV = 0.70710678f;
static constexpr float DENORMAL_OFFSET = 1e-18f;
static constexpr float INTERPOLATION_SPEED = 0.1f;

static constexpr std::array<float, NUM_EQ_BANDS> EQ_FREQUENCIES = {
        31.25f, 62.5f, 125.0f, 250.0f, 500.0f,
        1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f
};

struct alignas(16) EqBandInterpolator {
    std::atomic<float> targetGain{0.0f};
    std::atomic<float> currentGain{0.0f};
    float a0 = 1.0f, a1 = 0.0f, a2 = 0.0f, b1 = 0.0f, b2 = 0.0f;
    float z1 = 0.0f, z2 = 0.0f;
    bool active = false;

    inline void setTargetGain(float g) { targetGain.store(g, std::memory_order_release); }
    
    inline void updateInterpolation() {
        float target = targetGain.load(std::memory_order_acquire);
        float current = currentGain.load(std::memory_order_relaxed);
        float diff = target - current;
        if (std::abs(diff) > 0.001f) {
            currentGain.store(current + diff * INTERPOLATION_SPEED, std::memory_order_release);
        }
    }
    
    inline float process(float x) {
        if (!active) return x;
        updateInterpolation();
        float g = currentGain.load(std::memory_order_acquire);
        if (std::abs(g) < 0.01f) return x;
        float y = x * a0 + z1;
        z1 = x * a1 + z2 - b1 * y + DENORMAL_OFFSET;
        z2 = x * a2 - b2 * y;
        return y;
    }
    
    inline void setCoefficients(float sr, float f, float g, float bw) {
        const bool isActive = std::abs(g) > 0.1f;
        active = isActive;
        if (!isActive) return;
        const float A = powf(10.0f, g / 40.0f);
        const float w = 2.0f * static_cast<float>(M_PI) * f / sr;
        const float alpha = sinf(w) * sinhf(logf(2.0f) / 2.0f * bw * w / sinf(w));
        const float c = cosf(w);
        const float a0_raw = 1.0f + alpha / A;
        const float invA0 = 1.0f / a0_raw;
        a0 = (1.0f + alpha * A) * invA0;
        a1 = (-2.0f * c) * invA0;
        a2 = (1.0f - alpha * A) * invA0;
        b1 = (-2.0f * c) * invA0;
        b2 = (1.0f - alpha / A) * invA0;
    }
};

struct alignas(16) BassFilter {
    alignas(16) float a0 = 1.2f, a1 = 1.2f, a2 = 1.2f, b1 = 0.0f, b2 = 0.0f;
    alignas(16) float z1 = 0.0f, z2 = 0.0f;
    std::atomic<bool> active{false};
    std::atomic<float> targetGain{0.0f};
    std::atomic<float> currentGain{0.0f};

    inline void updateInterpolation() {
        float target = targetGain.load(std::memory_order_acquire);
        float current = currentGain.load(std::memory_order_relaxed);
        float diff = target - current;
        if (std::abs(diff) > 0.001f) {
            currentGain.store(current + diff * INTERPOLATION_SPEED, std::memory_order_release);
        }
    }

    inline float process(float x) {
        if (!active.load(std::memory_order_acquire)) return x;
        updateInterpolation();
        float g = currentGain.load(std::memory_order_acquire);
        if (std::abs(g) < 0.01f) return x;
        float y = x * a0 + z1;
        z1 = x * a1 + z2 - b1 * y + DENORMAL_OFFSET;
        z2 = x * a2 - b2 * y;
        if(y > 1.2f) y = 1.2f; else if(y < -1.2f) y = -1.2f;
        return y;
    }

    void setCoefficients(float sr, float f, float g, float q){
        float A=powf(10.0f,g/40.0f);
        float w=2.0f*static_cast<float>(M_PI)*f/sr;
        float alpha=sinf(w)/2.0f*sqrtf((A+1.0f/A)*(1.0f/q-1.0f)+2.0f);
        float c=cosf(w),sqrtA=sqrtf(A);
        float a0_raw=(A+1.0f)+(A-1.0f)*c+2.0f*sqrtA*alpha;
        float invA0=1.0f/a0_raw;
        a0=A*((A+1.0f)-(A-1.0f)*c+2.0f*sqrtA*alpha)*invA0;
        a1=2.0f*A*((A-1.0f)-(A+1.0f)*c)*invA0;
        a2=A*((A+1.0f)-(A-1.0f)*c-2.0f*sqrtA*alpha)*invA0;
        b1=-2.0f*((A-1.0f)+(A+1.0f)*c)*invA0;
        b2=((A+1.0f)+(A-1.0f)*c-2.0f*sqrtA*alpha)*invA0;
    }
    
    void applyGain(float sr) {
        float g = currentGain.load(std::memory_order_acquire);
        setCoefficients(sr, 150.0f, g, SQRT_2_INV);
    }
};

class ReverbOptimized {
    struct DelayLine {
        float buffer[48000]{};
        int size = 48000;
        int pos = 0;

        inline float read(float delaySamples) {
            float readPos = static_cast<float>(pos) - delaySamples;
            if (readPos < 0.0f) readPos += static_cast<float>(size);

            int i1 = static_cast<int>(readPos);
            int i2 = (i1 + 1) % size;
            float frac = readPos - static_cast<float>(i1);

            return buffer[i1] * (1.0f - frac) + buffer[i2] * frac;
        }

        inline void write(float x) {
            buffer[pos] = x;
            pos++;
            if (pos >= size) pos = 0;
        }
    };

    DelayLine delays[8];

    float feedback[8] = {
            0.78f, 0.80f, 0.82f, 0.84f,
            0.76f, 0.79f, 0.81f, 0.83f
    };

    float baseDelay[8] = {
            1423.0f, 1557.0f, 1617.0f, 1789.0f,
            1867.0f, 1999.0f, 2137.0f, 2251.0f
    };

    float modPhase[8] = {};
    float modSpeed[8] = {
            0.10f, 0.12f, 0.09f, 0.11f,
            0.13f, 0.08f, 0.14f, 0.07f
    };

public:
    std::atomic<float> mix{0.0f};

    inline float processSample(float x) {
        float m = mix.load(std::memory_order_relaxed);
        if (m < 0.01f) return x;
        float out = 0.0f;

#pragma GCC unroll 8
        for (int i = 0; i < 8; i++) {
            modPhase[i] += modSpeed[i];
            if (modPhase[i] > 2.0f * static_cast<float>(M_PI)) modPhase[i] -= 2.0f * static_cast<float>(M_PI);

            float mod = sinf(modPhase[i]) * 5.0f;

            float delayTime = baseDelay[i] + mod;

            float delayed = delays[i].read(delayTime);

            float input = x + delayed * feedback[i] + DENORMAL_OFFSET;

            delays[i].write(input);

            out += delayed;
        }

        return x * (1.0f - m) + (out * 0.125f) * m;
    }

    inline void processBlock(float* __restrict__ left, float* __restrict__ right, int count) {
        float m = mix.load(std::memory_order_relaxed);
        if (m < 0.01f) return;

        for (int i = 0; i < count; i++) {
            float l = processSample(left[i]);
            float r = processSample(right[i]);

            float wetL = l * 0.7f + r * 0.3f;
            float wetR = r * 0.7f + l * 0.3f;

            left[i] = wetL;
            right[i] = wetR;
        }
    }
};

class CompressorOptimized {
public:
    std::atomic<float> threshold{0.3f}, ratio{4.0f}, attack{0.08f}, release{0.8f};
    std::atomic<float> sampleRate{44100.0f};
    std::atomic<bool> enabled{false};
private:
    float envelopeL = 0.0f, envelopeR = 0.0f;
    float attackCoef = 0.0f, releaseCoef = 0.0f;
public:
    inline void updateCoefficients() {
        float a = attack.load(std::memory_order_relaxed);
        float r = release.load(std::memory_order_relaxed);
        float sr = sampleRate.load(std::memory_order_relaxed);

        attackCoef = expf(-1.0f / (a * sr));
        releaseCoef = expf(-1.0f / (r * sr));
    }
    inline void processBlock(float* __restrict__ buffer, int count, float& envelope) {
        updateCoefficients();
        float th = threshold.load(std::memory_order_acquire);
        float rt = ratio.load(std::memory_order_acquire);
        for(int i=0; i<count; i++){
            float absInput = fabsf(buffer[i]);
            envelope = (absInput > envelope) ? attackCoef*envelope + (1.0f-attackCoef)*absInput : releaseCoef*envelope + (1.0f-releaseCoef)*absInput;
            float gain = (envelope>th)? (th + (envelope-th)/rt)/(envelope+1e-9f) : 1.0f;
            buffer[i]*=gain;
        }
    }
    inline void process(float* __restrict__ left, float* __restrict__ right, int count) {
        if (!enabled.load(std::memory_order_acquire)) return;
        processBlock(left, count, envelopeL);
        processBlock(right, count, envelopeR);
    }
};

static std::atomic<bool> gEqEnabled{false};
static std::atomic<float> gStereoWidth{1.0f};
alignas(16) std::array<float, 4096> gLeftBuf, gRightBuf;
alignas(16) std::array<float, 256> gFFTData;

inline void fastFFT(std::complex<float>* __restrict__ data, int n) {
    for (int i = 1, j = 0; i < n; i++) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) std::swap(data[i], data[j]);
    }
    for (int len = 2; len <= n; len <<= 1) {
        float ang = -2.0f * static_cast<float>(M_PI) / static_cast<float>(len);
        std::complex<float> wlen(cosf(ang), sinf(ang));
        for (int i = 0; i < n; i += len) {
            std::complex<float> w(1.0f);
            for (int j = 0; j < len / 2; j++) {
                std::complex<float> u = data[i + j];
                std::complex<float> v = data[i + j + len / 2] * w;
                data[i + j] = u + v;
                data[i + j + len / 2] = u - v;
                w *= wlen;
            }
        }
    }
}

inline void applyHannWindowToReal(std::complex<float>* __restrict__ data, int size) {
    const auto fSizeMinus1 = static_cast<float>(size - 1);
    for (int i = 0; i < size; i++) {
        float window = 0.5f * (1.0f - cosf(2.0f * static_cast<float>(M_PI) * static_cast<float>(i) / fSizeMinus1));
        data[i] = std::complex<float>(data[i].real() * window, data[i].imag());
    }
}

inline float fastSoftClip(float x) {
    float ax = fabsf(x);
    float sign = x > 0.0f ? 1.0f : -1.0f;
    if (ax > 1.0f) return sign;
    return x * (1.5f - 0.5f * x * x);
}

static EqBandInterpolator gEqL[NUM_EQ_BANDS];
static EqBandInterpolator gEqR[NUM_EQ_BANDS];
static BassFilter gBassL, gBassR;
static CompressorOptimized gCompressor;
static ReverbOptimized gReverbL, gReverbR;
static std::array<std::complex<float>, FFT_SIZE> gFFTWork;
static int gEqUpdateCounter = 0;

inline void updateAllEqBands() {
    float sr = gSampleRate.load(std::memory_order_acquire);
    for (int b = 0; b < NUM_EQ_BANDS; b++) {
        float g = gEqL[b].targetGain.load(std::memory_order_acquire);
        gEqL[b].setCoefficients(sr, EQ_FREQUENCIES[static_cast<size_t>(b)], g, 1.0f);
        gEqR[b].setCoefficients(sr, EQ_FREQUENCIES[static_cast<size_t>(b)], g, 1.0f);
    }
    bool anyActive = false;
    for (auto const& band : gEqL) {
        if (std::abs(band.targetGain.load(std::memory_order_acquire)) > 0.1f) {
            anyActive = true;
            break;
        }
    }
    gEqEnabled.store(anyActive, std::memory_order_release);
}

extern "C" {

JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setSampleRate(JNIEnv*, jobject, jfloat sr) {
    gSampleRate.store(sr, std::memory_order_release);
    gCompressor.sampleRate.store(sr, std::memory_order_release);
    gBassL.applyGain(sr);
    gBassR.applyGain(sr);
    gEqUpdateCounter = 1;
}
JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setDrcEnabled(JNIEnv*, jobject, jboolean e) {
    gCompressor.enabled.store(e == JNI_TRUE, std::memory_order_release);
}
JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setReverbMix(JNIEnv*, jobject, jfloat m) {
    gReverbL.mix.store(m, std::memory_order_release);
    gReverbR.mix.store(m, std::memory_order_release);
}
JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setEqFull(JNIEnv* env, jobject thiz, jfloatArray gains) {
    if (!gains) return;
    
    jsize len = env->GetArrayLength(gains);
    int bandsToUpdate = std::min(static_cast<int>(len), NUM_EQ_BANDS);
    
    jfloat* gainsPtr = env->GetFloatArrayElements(gains, nullptr);
    if (!gainsPtr) return;
    
    for (int b = 0; b < bandsToUpdate; b++) {
        gEqL[b].setTargetGain(gainsPtr[b]);
        gEqR[b].setTargetGain(gainsPtr[b]);
    }
    
    gEqUpdateCounter = 1;
    
    env->ReleaseFloatArrayElements(gains, gainsPtr, JNI_ABORT);
}
JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setBassBoost(JNIEnv*, jobject, jfloat g) {
    float scaledGain = g * 4.0f;
    gBassL.targetGain.store(scaledGain, std::memory_order_release);
    gBassR.targetGain.store(scaledGain, std::memory_order_release);
    float sr = gSampleRate.load(std::memory_order_acquire);
    gBassL.applyGain(sr);
    gBassR.applyGain(sr);
    if (std::abs(g) > 0.01f) {
        gBassL.active.store(true, std::memory_order_release);
        gBassR.active.store(true, std::memory_order_release);
    } else {
        gBassL.active.store(false, std::memory_order_release);
        gBassR.active.store(false, std::memory_order_release);
    }
}
JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setStereoWidth(JNIEnv*, jobject, jfloat w) {
    gStereoWidth.store(fmaxf(0.0f, fminf(w, 2.0f)), std::memory_order_release);
}

JNIEXPORT jfloatArray JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_getFftData(JNIEnv* env, jobject) {
    jfloatArray arr = env->NewFloatArray(256);
    env->SetFloatArrayRegion(arr, 0, 256, gFFTData.data());
    return arr;
}

inline void computeLogarithmicFFT(float* output, const std::complex<float>* input, int inputSize) {
    float sr = gSampleRate.load(std::memory_order_acquire);
    float binWidth = sr / (2.0f * static_cast<float>(inputSize));
    constexpr int NUM_BANDS = 256;
    constexpr float MIN_FREQ = 20.0f;
    constexpr float MAX_FREQ = 20000.0f;
    float logMin = logf(MIN_FREQ);
    float logMax = logf(MAX_FREQ);
    float logRange = logMax - logMin;
    
    for (int b = 0; b < NUM_BANDS; b++) {
        float f1 = expf(logMin + (logRange * static_cast<float>(b) / static_cast<float>(NUM_BANDS)));
        float f2 = expf(logMin + (logRange * static_cast<float>(b + 1) / static_cast<float>(NUM_BANDS)));
        int idx1 = static_cast<int>(f1 / binWidth);
        int idx2 = static_cast<int>(f2 / binWidth);
        idx1 = std::max(0, std::min(idx1, inputSize - 1));
        idx2 = std::max(0, std::min(idx2, inputSize - 1));
        
        float sum = 0.0f;
        int count = idx2 - idx1 + 1;
        for (int i = idx1; i <= idx2 && i < inputSize; i++) {
            sum += std::abs(input[i]);
        }
        float avg = (count > 0) ? sum / static_cast<float>(count) : 0.0f;
        output[b] = avg * 0.5f;
    }
}

JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_processAudioDirect(JNIEnv* env, jobject, jobject byteBuffer, jint size) {
    auto* buffer = static_cast<jshort*>(env->GetDirectBufferAddress(byteBuffer));
    if (!buffer) return;
    int numFrames = (size / 2) / 2;
    if (numFrames > 4096) numFrames = 4096;

    if (gEqUpdateCounter > 0) {
        updateAllEqBands();
        gEqUpdateCounter--;
    }

    for (int i = 0; i < numFrames; i++) {
        gLeftBuf[static_cast<size_t>(i)] = static_cast<float>(buffer[i * 2]) * INV_32768;
        gRightBuf[static_cast<size_t>(i)] = static_cast<float>(buffer[i * 2 + 1]) * INV_32768;
    }

    bool eqEnabled = gEqEnabled.load(std::memory_order_relaxed);
    if (eqEnabled) {
        for (int i = 0; i < numFrames; i++) {
            float xL = gLeftBuf[static_cast<size_t>(i)];
            float xR = gRightBuf[static_cast<size_t>(i)];

            for (int b = 0; b < NUM_EQ_BANDS; b++) {
                float g = gEqL[b].currentGain.load(std::memory_order_relaxed);
                if (std::abs(g) < 0.01f) continue;

                xL = gEqL[b].process(xL);
                xR = gEqR[b].process(xR);
            }

            gLeftBuf[static_cast<size_t>(i)] = xL;
            gRightBuf[static_cast<size_t>(i)] = xR;
        }
    }

    for(int i = 0; i < numFrames; i++) {
        gLeftBuf[static_cast<size_t>(i)] = gBassL.process(gLeftBuf[static_cast<size_t>(i)]);
        gRightBuf[static_cast<size_t>(i)] = gBassR.process(gRightBuf[static_cast<size_t>(i)]);
    }

    gReverbL.processBlock(gLeftBuf.data(), gRightBuf.data(), numFrames);

    float stereoWidth = gStereoWidth.load(std::memory_order_relaxed);
    if (stereoWidth != 1.0f) {
        float halfWidth = stereoWidth * 0.5f;
        for (int j = 0; j < numFrames; j++) {
            float mid = (gLeftBuf[static_cast<size_t>(j)] + gRightBuf[static_cast<size_t>(j)]) * 0.5f;
            float side = (gLeftBuf[static_cast<size_t>(j)] - gRightBuf[static_cast<size_t>(j)]) * halfWidth;
            gLeftBuf[static_cast<size_t>(j)] = mid + side;
            gRightBuf[static_cast<size_t>(j)] = mid - side;
        }
    }

    gCompressor.process(gLeftBuf.data(), gRightBuf.data(), numFrames);

    if (numFrames >= FFT_SIZE) {
        for (int k = 0; k < FFT_SIZE; k++) {
            gFFTWork[static_cast<size_t>(k)] = std::complex<float>(gLeftBuf[static_cast<size_t>(k)], 0.0f);
        }
    } else {
        for (int k = 0; k < numFrames; k++) {
            gFFTWork[static_cast<size_t>(k)] = std::complex<float>(gLeftBuf[static_cast<size_t>(k)], 0.0f);
        }
        for (int k = numFrames; k < FFT_SIZE; k++) {
            gFFTWork[static_cast<size_t>(k)] = std::complex<float>(0.0f, 0.0f);
        }
    }
    
    applyHannWindowToReal(gFFTWork.data(), FFT_SIZE);
    fastFFT(gFFTWork.data(), FFT_SIZE);
    computeLogarithmicFFT(gFFTData.data(), gFFTWork.data(), FFT_SIZE / 2);

    for (int k = 0; k < numFrames; k++) {
        buffer[k * 2] = static_cast<jshort>(fastSoftClip(gLeftBuf[static_cast<size_t>(k)]) * 32767.0f);
        buffer[k * 2 + 1] = static_cast<jshort>(fastSoftClip(gRightBuf[static_cast<size_t>(k)]) * 32767.0f);
    }
}
}

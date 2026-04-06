#include <jni.h>
#include <vector>
#include <cmath>
#include <complex>
#include <array>

#if defined(__ARM_NEON)
#include <arm_neon.h>
#endif

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static constexpr int FFT_SIZE = 512;
static constexpr int NUM_EQ_BANDS = 10;
static constexpr float INV_32768 = 1.0f / 32768.0f;
static constexpr float SQRT_2_INV = 0.70710678f;
static constexpr float DENORMAL_OFFSET = 1e-18f;

static constexpr std::array<float, NUM_EQ_BANDS> EQ_FREQUENCIES = {
        31.25f, 62.5f, 125.0f, 250.0f, 500.0f,
        1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f
};

struct alignas(16) BiquadBank {
    alignas(16) std::array<float, NUM_EQ_BANDS> a0{}, a1{}, a2{}, b1{}, b2{};
    alignas(16) std::array<float, NUM_EQ_BANDS> z1{}, z2{};
    uint16_t activeMask = 0;

    [[nodiscard]] inline bool hasActiveBands() const { return activeMask != 0; }

    inline void setBandActive(int band, bool active) {
        if (active) activeMask |= (1 << band);
        else activeMask &= ~(1 << band);
    }

    inline void processBlock(float* __restrict__ data, int count) {
        if (!this -> hasActiveBands()) return;
        for (int i = 0; i < count; i++) {
            float x = data[i];
#pragma GCC unroll 10
            for (int b = 0; b < NUM_EQ_BANDS; b++) {
                if (activeMask & (1 << b)) {
                    float y = x * a0[b] + z1[b];
                    z1[b] = x * a1[b] + z2[b] - b1[b] * y + DENORMAL_OFFSET;
                    z2[b] = x * a2[b] - b2[b] * y;
                    x = y;
                }
            }
            data[i] = x;
        }
    }

    void setPeakingEQ(int band, float sr, float f, float g, float bw) {
        if (band < 0 || band >= NUM_EQ_BANDS) return;
        const bool active = std::abs(g) > 0.1f;
        setBandActive(band, active);
        if (!active) return;
        const float A = powf(10.0f, g / 40.0f);
        const float w = 2.0f * static_cast<float>(M_PI) * f / sr;
        const float alpha = sinf(w) * sinhf(logf(2.0f) / 2.0f * bw * w / sinf(w));
        const float c = cosf(w);
        const float a0_raw = 1.0f + alpha / A;
        const float invA0 = 1.0f / a0_raw;
        a0[band] = (1.0f + alpha * A) * invA0;
        a1[band] = (-2.0f * c) * invA0;
        a2[band] = (1.0f - alpha * A) * invA0;
        b1[band] = (-2.0f * c) * invA0;
        b2[band] = (1.0f - alpha / A) * invA0;
    }
};

struct alignas(16) BassFilter {
    alignas(16) float a0 = 1.2f, a1 = 1.2f, a2 = 1.2f, b1 = 0.0f, b2 = 0.0f;
    alignas(16) float z1 = 0.0f, z2 = 0.0f;
    bool active = false;

    inline float process(float x) {
        if (!active) return x;
        float y = x * a0 + z1;
        z1 = x * a1 + z2 - b1 * y + DENORMAL_OFFSET;
        z2 = x * a2 - b2 * y;
        if(y > 1.2f) y = 1.2f; else if(y < -1.2f) y = -1.2f;
        return y;
    }

    void setLowShelf(float sr,float f,float g,float q){
        active=std::abs(g)>0.01f;
        if(!active) return;
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
};

template<int SIZE>
struct CircularBuffer {
    alignas(16) std::array<float, SIZE> data = {};
    int pos = 0;
    [[nodiscard]] inline float read() const { return data[pos]; }
    inline void write(float v) { data[pos] = v; }
    inline void advance() { pos = (pos + 1) % SIZE; }
};

class ReverbOptimized {
    std::array<CircularBuffer<1116>, 4> combs;
    std::array<CircularBuffer<556>, 2> allpasses;
    std::array<float, 4> combFeedback = {0.841f, 0.815f, 0.796f, 0.771f};
    float mix = 0.0f;
public:
    inline void setMix(float m) { mix = m; }
    inline float process(float x) {
        if (mix < 0.01f) return x;
        float out = 0.0f;
#pragma GCC unroll 4
        for (int i = 0; i < 4; i++) {
            float delayed = combs[i].read();
            out += delayed;
            combs[i].write(x + delayed * combFeedback[i] + DENORMAL_OFFSET);
            combs[i].advance();
        }
        out *= 0.25f;
        for (int i = 0; i < 2; i++) {
            float bufOut = allpasses[i].read();
            float xOut = -0.5f * out + bufOut;
            allpasses[i].write(out + 0.5f * bufOut);
            allpasses[i].advance();
            out = xOut;
        }
        return x * (1.0f - mix) + out * mix;
    }
    inline void processBlock(float* __restrict__ left, float* __restrict__ right, int count) {
        if (mix < 0.01f) return;
        for (int i = 0; i < count; i++) {
            left[i] = process(left[i]);
            right[i] = process(right[i]);
        }
    }
};

class CompressorOptimized {
public:
    float threshold = 0.3f, ratio = 4.0f, attack = 0.08f, release = 0.8f, sampleRate = 44100.0f;
private:
    float envelopeL = 0.0f, envelopeR = 0.0f;
    float attackCoef = 0.0f, releaseCoef = 0.0f;
    bool coefficientsValid = false;
public:
    inline void updateCoefficients() {
        if (coefficientsValid) return;
        attackCoef = expf(-1.0f / (attack * sampleRate));
        releaseCoef = expf(-1.0f / (release * sampleRate));
        coefficientsValid = true;
    }
    inline void processBlock(float* __restrict__ buffer, int count, float& envelope) {
        updateCoefficients();
        for(int i=0; i<count; i++){
            float absInput = fabsf(buffer[i]);
            envelope = (absInput > envelope) ? attackCoef*envelope + (1-attackCoef)*absInput : releaseCoef*envelope + (1-releaseCoef)*absInput;
            float gain = (envelope>threshold)? (threshold + (envelope-threshold)/ratio)/(envelope+1e-9f) : 1.0f;
            buffer[i]*=gain;
        }
    }
    inline void process(float* __restrict__ left, float* __restrict__ right, int count) {
        processBlock(left, count, envelopeL);
        processBlock(right, count, envelopeR);
    }
};

CompressorOptimized gCompressor;
ReverbOptimized gReverbL, gReverbR;
BiquadBank gEqL, gEqR;
BassFilter gBassL, gBassR;
bool gDrcEnabled = false, gEqEnabled = false, gBassBoostEnabled = false;
float gStereoWidth = 1.0f;
alignas(16) std::array<float, 4096> gLeftBuf, gRightBuf;
alignas(16) std::array<float, 256> gFFTData;
alignas(16) std::array<std::complex<float>, FFT_SIZE> gFFTWork;

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

inline float fastSoftClip(float x) {
    float ax = fabsf(x);
    float sign = x > 0 ? 1.0f : -1.0f;
    if (ax > 1.0f) return sign;
    return x * (1.5f - 0.5f * x * x);
}

extern "C" {

JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setDrcEnabled(JNIEnv*, jobject, jboolean e) { gDrcEnabled = e; }
JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setReverbMix(JNIEnv*, jobject, jfloat m) { gReverbL.setMix(m); gReverbR.setMix(m); }
JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setEqBand(JNIEnv*, jobject, jint b, jfloat g) {
    if (b >= 0 && b < NUM_EQ_BANDS) {
        gEqL.setPeakingEQ(b, 44100.0f, EQ_FREQUENCIES[b], g, 1.0f);
        gEqR.setPeakingEQ(b, 44100.0f, EQ_FREQUENCIES[b], g, 1.0f);
    }
    gEqEnabled = gEqL.hasActiveBands();
}
JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setBassBoost(JNIEnv*, jobject, jfloat g) {
    if (g > 0.01f) {
        gBassL.setLowShelf(44100.0f, 150.0f, g, SQRT_2_INV);
        gBassR.setLowShelf(44100.0f, 150.0f, g, SQRT_2_INV);
        gBassBoostEnabled = true;
    } else { gBassBoostEnabled = false; }
}
JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setStereoWidth(JNIEnv*, jobject, jfloat w) { gStereoWidth = fmaxf(0.0f, fminf(w, 2.0f)); }

JNIEXPORT jfloatArray JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_getFftData(JNIEnv* env, jobject) {
    jfloatArray arr = env->NewFloatArray(256);
    env->SetFloatArrayRegion(arr, 0, 256, gFFTData.data());
    return arr;
}

JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_processAudioDirect(JNIEnv* env, jobject, jobject byteBuffer, jint size) {
    auto* buffer = static_cast<jshort*>(env->GetDirectBufferAddress(byteBuffer));
    if (!buffer) return;
    int numFrames = (size / 2) / 2;
    if (numFrames > 4096) numFrames = 4096;

    for (int i = 0; i < numFrames; i++) {
        gLeftBuf[i] = static_cast<float>(buffer[i * 2]) * INV_32768;
        gRightBuf[i] = static_cast<float>(buffer[i * 2 + 1]) * INV_32768;
    }

    if (gEqEnabled) { gEqL.processBlock(gLeftBuf.data(), numFrames); gEqR.processBlock(gRightBuf.data(), numFrames); }
    if (gBassBoostEnabled) {
        for(int i=0; i<numFrames; i++) { gLeftBuf[i] = gBassL.process(gLeftBuf[i]); gRightBuf[i] = gBassR.process(gRightBuf[i]); }
    }
    gReverbL.processBlock(gLeftBuf.data(), gRightBuf.data(), numFrames);

    if (gStereoWidth != 1.0f) {
        float halfWidth = gStereoWidth * 0.5f;
        for (int j = 0; j < numFrames; j++) {
            float mid = (gLeftBuf[j] + gRightBuf[j]) * 0.5f;
            float side = (gLeftBuf[j] - gRightBuf[j]) * halfWidth;
            gLeftBuf[j] = mid + side; gRightBuf[j] = mid - side;
        }
    }

    if (gDrcEnabled) gCompressor.process(gLeftBuf.data(), gRightBuf.data(), numFrames);

    // FFT for visualization
    for (int k = 0; k < FFT_SIZE; k++) {
        gFFTWork[k] = (k < 256 && k < numFrames) ? std::complex<float>(gLeftBuf[k], 0.0f) : std::complex<float>(0.0f, 0.0f);
    }
    fastFFT(gFFTWork.data(), FFT_SIZE);
    for (int k = 0; k < 256; k++) {
        gFFTData[k] = std::abs(gFFTWork[k]) * 0.5f; // Increased scale
    }

    for (int k = 0; k < numFrames; k++) {
        buffer[k * 2] = static_cast<jshort>(fastSoftClip(gLeftBuf[k]) * 32767.0f);
        buffer[k * 2 + 1] = static_cast<jshort>(fastSoftClip(gRightBuf[k]) * 32767.0f);
    }
}
}

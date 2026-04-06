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

// =============================================================================
// OPTIMIZED CONFIGURATION
// =============================================================================

// Use L1/L2 cache-optimized block size (typical L1: 32KB, L2: 256KB)
static constexpr int FFT_SIZE = 512;
static constexpr int NUM_EQ_BANDS = 10;

// Pre-compute constants at compile time
static constexpr float INV_32768 = 1.0f / 32768.0f;
static constexpr float SQRT_2_INV = 0.70710678f;  // 1/sqrt(2)

// Denormal protection - use single scalar instead of adding per-sample
static constexpr float DENORMAL_OFFSET = 1e-18f;

// EQ frequencies - static const for compile-time access
static constexpr std::array<float, NUM_EQ_BANDS> EQ_FREQUENCIES = {
        31.25f, 62.5f, 125.0f, 250.0f, 500.0f,
        1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f
};

// =============================================================================
// OPTIMIZED DSP CLASSES - Structure of Arrays (SoA) for cache efficiency
// =============================================================================

struct alignas(16) BiquadBank {
    // Coefficients (SoA - better for SIMD loads)
    alignas(16) std::array<float, NUM_EQ_BANDS> a0{}, a1{}, a2{}, b1{}, b2{};
    // State variables
    alignas(16) std::array<float, NUM_EQ_BANDS> z1{}, z2{};
    // Active flags (packed into bitmask for branch-free processing)
    uint16_t activeMask = 0;

    // Pre-check if any EQ band is active - branch free
    [[nodiscard]] inline bool hasActiveBands() const { return activeMask != 0; }

    inline void setBandActive(int band, bool active) {
        if (active) activeMask |= (1 << band);
        else activeMask &= ~(1 << band);
    }

    // Optimized bulk processing for a single channel
    inline void processBlock(float* __restrict__ data, int count) {
        if (!hasActiveBands()) return;

        for (int i = 0; i < count; i++) {
            float x = data[i];
            // Process all bands (compiler will optimize for activeMask)
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

// =============================================================================
// BASS BOOST
// =============================================================================
struct alignas(16) BassFilter {
    alignas(16) float a0 = 1.2f, a1 = 1.2f, a2 = 1.2f, b1 = 0.0f, b2 = 0.0f;
    alignas(16) float z1 = 0.0f, z2 = 0.0f;
    bool active = false;

    inline float process(float x) {
        if (!active) return x;
        float y = x * a0 + z1;
        z1 = x * a1 + z2 - b1 * y + DENORMAL_OFFSET;
        z2 = x * a2 - b2 * y;
        if(y>1.2f) y=1.2f;
        else if(y<-1.2f) y=-1.2f;
        return y;
    }

    inline void processNEON(float* __restrict__ data, int count) {
#if defined(__ARM_NEON)
        if (!active || count < 4) { for(int i=0;i<count;i++) data[i]=process(data[i]); return; }

        // Scalar feedback for stability
        for(int i=0;i<count;i++){
            float x = data[i];
            float y = a0*x + z1;
            z1 = a1*x + z2 - b1*y + DENORMAL_OFFSET;
            z2 = a2*x - b2*y;
            if(y>1.2f) y=1.2f;
            else if(y<-1.2f) y=-1.2f;
            data[i] = y;
        }
#else
        for(int i=0;i<count;i++) data[i]=process(data[i]);
#endif
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

// =============================================================================
// LOCK-FREE REVERB - Fixed-size circular buffers (no heap allocation)
// =============================================================================

template<int SIZE>
struct CircularBuffer {
    alignas(16) std::array<float, SIZE> data = {};
    int pos = 0;

    [[nodiscard]] inline float read() const { return data[pos]; }
    inline void write(float v) { data[pos] = v; }
    inline void advance() { pos = (pos + 1) % SIZE; }

};

class ReverbOptimized {
    // Classic Schroeder: 4 parallel comb filters + 2 series allpass
    // Fixed buffer sizes for lock-free operation
    std::array<CircularBuffer<1116>, 4> combs;
    std::array<CircularBuffer<556>, 2> allpasses;
    std::array<float, 4> combFeedback = {0.841f, 0.815f, 0.796f, 0.771f};

    float mix = 0.0f;

public:
    ReverbOptimized() = default;

    inline void setMix(float m) { mix = m; }

    // Branch-free processing with inline inlining
    inline float process(float x) {
        if (mix < 0.01f) return x;

        // Parallel comb filters (unrolled for ARM NEON)
        float out = 0.0f;
#pragma GCC unroll 4
        for (int i = 0; i < 4; i++) {
            float delayed = combs[i].read();
            out += delayed;
            combs[i].write(x + delayed * combFeedback[i] + DENORMAL_OFFSET);
            combs[i].advance();
        }
        out *= 0.25f;  // 1/4 normalization

        // Series allpass filters
        for (int i = 0; i < 2; i++) {
            float bufOut = allpasses[i].read();
            float xOut = -0.5f * out + bufOut;
            allpasses[i].write(out + 0.5f * bufOut);
            allpasses[i].advance();
            out = xOut;
        }

        return x * (1.0f - mix) + out * mix;
    }

    // NEON-optimized block processing
    inline void processBlock(float* __restrict__ left, float* __restrict__ right, int count) {
        if (mix < 0.01f) return;

        for (int i = 0; i < count; i++) {
            left[i] = process(left[i]);
            right[i] = process(right[i]);
        }
    }
};

// =============================================================================
// OPTIMIZED COMPRESSOR - Per-channel state, branch-free envelope
// =============================================================================

class CompressorOptimized {
public:
    float threshold = 0.3f;
    float ratio = 4.0f;
    float attack = 0.01f;
    float release = 0.2f;
    float sampleRate = 44100.0f;

private:
    // Per-channel envelope state
    float envelopeL = 0.0f;
    float envelopeR = 0.0f;

    // Pre-computed coefficients
    float attackCoef = 0.0f;
    float releaseCoef = 0.0f;
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

        for (int i = 0; i < count; i++) {
            float absInput = fabsf(buffer[i]);
            // Branch-free envelope attack/release
            bool attackMode = absInput > envelope;
            envelope = attackMode
                    ? attackCoef * envelope + (1.0f - attackCoef) * absInput
                    : releaseCoef * envelope + (1.0f - releaseCoef) * absInput;

            // Soft-knee compression
            if (envelope > threshold) {
                float gainReduction = threshold + (envelope - threshold) / ratio;
                buffer[i] *= (gainReduction / (envelope + 1e-9f));
            }
        }
    }

    inline void process(float* __restrict__ left, float* __restrict__ right, int count) {
        processBlock(left, count, envelopeL);
        processBlock(right, count, envelopeR);
    }
};

// =============================================================================
// GLOBAL ENGINE - SoA layout for cache efficiency
// =============================================================================

CompressorOptimized gCompressor;
ReverbOptimized gReverbL, gReverbR;
BiquadBank gEqL, gEqR;
BassFilter gBassL, gBassR;

// Global state flags
bool gDrcEnabled = false;
bool gEqEnabled = false;  // Derived from gEqL.hasActiveBands()
bool gBassBoostEnabled = false;
float gStereoWidth = 1.0f;
float gTargetRMS = 0.20f;
float gCurrentGain = 1.0f;

// Pre-allocated buffers - fixed size to avoid heap allocation in real-time
alignas(16) std::array<float, 4096> gLeftBuf;
alignas(16) std::array<float, 4096> gRightBuf;
alignas(16) std::array<float, 256> gFFTData;
alignas(16) std::array<std::complex<float>, FFT_SIZE> gFFTWork;

// Fast FFT - iterative Cooley-Tukey
inline void fastFFT(std::complex<float>* __restrict__ data, int n) {
    // Bit-reversal permutation (iterative, cache-friendly)
    for (int i = 1, j = 0; i < n; i++) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) std::swap(data[i], data[j]);
    }

    // Cooley-Tukey stages
    for (int len = 2; len <= n; len <<= 1) {
        float ang = -2.0f * static_cast<float>(M_PI) / static_cast<float>(len);
        // Pre-compute wlen - critical for performance
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

// =============================================================================
// HIGH-PERFORMANCE AUDIO PROCESSING
// =============================================================================

// Fast soft clipping with polynomial approximation
inline float fastSoftClip(float x) {
    // Branchless clipping using min/max
    float ax = fabsf(x);
    float sign = x > 0 ? 1.0f : -1.0f;
    if (ax > 1.0f) return sign;
    return x * (1.4f - 0.4f * x * x);
}

// NEON-optimized auto gain with RMS calculation
inline void applyAutoGain(float* __restrict__ buffer, int count) {
    if (count <= 0) return;

    float sumSq = 0.0f;

#if defined(__ARM_NEON)
    // NEON vectorized sum of squares
    float32x4_t sumVec = vdupq_n_f32(0.0f);
    int i = 0;
    for (; i <= count - 4; i += 4) {
        float32x4_t v = vld1q_f32(buffer + i);
        sumVec = vmlaq_f32(sumVec, v, v);  // sum += v*v
    }
    // Horizontal add
    float32x2_t sumLo = vget_low_f32(sumVec);
    float32x2_t sumHi = vget_high_f32(sumVec);
    float32x2_t sumPair = vadd_f32(sumLo, sumHi);
    sumSq = vget_lane_f32(sumPair, 0) + vget_lane_f32(sumPair, 1);
#endif
    // Scalar tail
    for (int i = (count & ~3); i < count; i++) {
        sumSq += buffer[i] * buffer[i];
    }

    float rms = sqrtf(sumSq / static_cast<float>(count));
    if (rms > 0.001f) {
        float target = gTargetRMS / rms;
        // Smooth gain transition (exponential moving average)
        gCurrentGain = gCurrentGain * 0.99f + target * 0.01f;
        gCurrentGain = fminf(gCurrentGain, 2.0f);

        // NEON vectorized gain application
#if defined(__ARM_NEON)
        float32x4_t gVec = vdupq_n_f32(gCurrentGain);
        int j = 0;
        for (; j <= count - 4; j += 4) {
            float32x4_t v = vld1q_f32(buffer + j);
            vst1q_f32(buffer + j, vmulq_f32(v, gVec));
        }
#endif
        for (int j = (count & ~3); j < count; j++) {
            buffer[j] *= gCurrentGain;
        }
    }
}

// Main processing function - heavily optimized
extern "C" {

JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setDrcEnabled(JNIEnv*, jobject, jboolean e) {
    gDrcEnabled = e;
}

JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setReverbMix(JNIEnv*, jobject, jfloat m) {
    gReverbL.setMix(m);
    gReverbR.setMix(m);
}

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
    } else {
        gBassBoostEnabled = false;
    }
}

JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_setStereoWidth(JNIEnv*, jobject, jfloat w) {
    gStereoWidth = fmaxf(0.0f, fminf(w, 2.0f));
}

JNIEXPORT jfloatArray JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_getFftData(JNIEnv* env, jobject) {
    jfloatArray arr = env->NewFloatArray(256);
    env->SetFloatArrayRegion(arr, 0, 256, gFFTData.data());
    return arr;
}

JNIEXPORT void JNICALL Java_com_michatec_radio_helpers_NativeAudioProcessor_processAudioDirect(JNIEnv* env, jobject, jobject byteBuffer, jint size) {
    auto* buffer = static_cast<jshort*>(env->GetDirectBufferAddress(byteBuffer));
    if (!buffer) return;

    int numFrames = (size / 2) / 2;
    if (numFrames > 4096) numFrames = 4096;  // Clamp to buffer size

    // =========================================================================
    // STAGE 1: Convert to Float (NEON optimized, interleaved stereo)
    // =========================================================================
    int i = 0;
#if defined(__ARM_NEON)
    float32x4_t invScale = vdupq_n_f32(INV_32768);
    for (; i <= numFrames - 4; i += 4) {
        // Load interleaved 16-bit stereo, deinterleave to two floats
        int16x4x2_t raw = vld2_s16(buffer + i * 2);
        // Expand to 32-bit, convert to float, scale
        float32x4_t left = vmulq_f32(vcvtq_f32_s32(vmovl_s16(raw.val[0])), invScale);
        float32x4_t right = vmulq_f32(vcvtq_f32_s32(vmovl_s16(raw.val[1])), invScale);
        vst1q_f32(gLeftBuf.data() + i, left);
        vst1q_f32(gRightBuf.data() + i, right);
    }
#endif
    // Scalar tail
    for (; i < numFrames; i++) {
        gLeftBuf[i] = static_cast<float>(buffer[i * 2]) * INV_32768;
        gRightBuf[i] = static_cast<float>(buffer[i * 2 + 1]) * INV_32768;
    }

    // =========================================================================
    // STAGE 2: DSP Chain (EQ -> Bass -> Reverb -> Stereo Width)
    // =========================================================================

    // EQ processing (branch-free based on active mask)
    if (gEqEnabled) {
        gEqL.processBlock(gLeftBuf.data(), numFrames);
        gEqR.processBlock(gRightBuf.data(), numFrames);
    }

    // Bass boost
    if (gBassBoostEnabled) {
        gBassL.processNEON(gLeftBuf.data(), numFrames);
        gBassR.processNEON(gRightBuf.data(), numFrames);
    }

    // Reverb
    gReverbL.processBlock(gLeftBuf.data(), gRightBuf.data(), numFrames);

    // Stereo width processing (branch-free)
    if (gStereoWidth != 1.0f) {
        float halfWidth = gStereoWidth * 0.5f;
        for (int j = 0; j < numFrames; j++) {
            float mid = (gLeftBuf[j] + gRightBuf[j]) * 0.5f;
            float side = (gLeftBuf[j] - gRightBuf[j]) * halfWidth;
            gLeftBuf[j] = mid + side;
            gRightBuf[j] = mid - side;
        }
    }

    // =========================================================================
    // STAGE 3: Dynamic Control (AutoGain -> Compressor)
    // =========================================================================
    applyAutoGain(gLeftBuf.data(), numFrames);
    applyAutoGain(gRightBuf.data(), numFrames);

    if (gDrcEnabled) {
        gCompressor.process(gLeftBuf.data(), gRightBuf.data(), numFrames);
    }

    // =========================================================================
    // STAGE 4: FFT Analysis (downsampled for visualization)
    // =========================================================================
    // Zero-pad for FFT (use first 256 samples only)
    for (int k = 0; k < FFT_SIZE; k++) {
        gFFTWork[k] = (k < 256) ? std::complex<float>(gLeftBuf[k], 0.0f) : std::complex<float>(0.0f, 0.0f);
    }
    fastFFT(gFFTWork.data(), FFT_SIZE);

    // Compute magnitude spectrum (only first 256 bins)
    for (int k = 0; k < 256; k++) {
        gFFTData[k] = std::abs(gFFTWork[k]) * 0.05f;
    }

    // =========================================================================
    // STAGE 5: Convert back to 16-bit with soft clipping
    // =========================================================================
    for (int k = 0; k < numFrames; k++) {
        buffer[k * 2] = static_cast<jshort>(fastSoftClip(gLeftBuf[k]) * 32767.0f);
        buffer[k * 2 + 1] = static_cast<jshort>(fastSoftClip(gRightBuf[k]) * 32767.0f);
    }
}

} // extern "C"
#include <jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>
#include <android/log.h>
#include <vector>
#include <algorithm>
#include <cmath>

extern "C" {

JNIEXPORT void JNICALL
Java_com_michatec_radio_helpers_ExtrasHelper_visualize(JNIEnv *env, jclass clazz, jobject surface, jfloatArray data) {
    if (!surface) return;
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) return;

    jsize len = env->GetArrayLength(data);
    if (len == 0) {
        ANativeWindow_release(window);
        return;
    }
    jfloat* body = env->GetFloatArrayElements(data, nullptr);

    ANativeWindow_Buffer buffer;
    ANativeWindow_setBuffersGeometry(window, 0, 0, WINDOW_FORMAT_RGBA_8888);

    if (ANativeWindow_lock(window, &buffer, nullptr) == 0) {
        auto* pixels = static_cast<uint32_t*>(buffer.bits);

        // Clear background (Dark Grey)
        for (int y = 0; y < buffer.height; y++) {
            uint32_t* row = pixels + (y * buffer.stride);
            for (int x = 0; x < buffer.width; x++) {
                row[x] = 0xFF121212;
            }
        }

        // Draw bars - fewer bins = thicker bars
        int displayBins = 40;
        float barWidth = static_cast<float>(buffer.width) / static_cast<float>(displayBins);
        int padding = static_cast<int>(barWidth * 0.2f);
        if (padding < 1) padding = 1;

        for (int i = 0; i < displayBins; i++) {
            // Map display bin to data index
            int dataIdx = (i * len) / displayBins;
            float val = body[dataIdx];

            // Use square root to compress the range (so peaks don't hit the top too easily)
            // and a lower multiplier (0.4f) to reduce overall height
            float scaledVal = sqrtf(val) * 0.5f;
            int barHeight = static_cast<int>(scaledVal * static_cast<float>(buffer.height));

            // Cap height at 75% to leave some room at the top
            int maxH = static_cast<int>(static_cast<float>(buffer.height) * 0.75f);
            if (barHeight > maxH) barHeight = maxH;
            if (barHeight < 4) barHeight = 4; // Minimal visible line

            int startX = static_cast<int>(static_cast<float>(i) * barWidth);
            int endX = static_cast<int>(static_cast<float>(i + 1) * barWidth);

            int drawStartX = startX + padding;
            int drawEndX = endX - padding;
            if (drawEndX <= drawStartX) drawEndX = drawStartX + 1;

            int barBottom = buffer.height - 4; // Bottom margin
            int barTop = barBottom - barHeight;

            for (int x = drawStartX; x < drawEndX; x++) {
                if (x < 0 || x >= buffer.width) continue;
                for (int y = barTop; y < barBottom; y++) {
                    if (y < 0 || y >= buffer.height) continue;
                    // Using the same color, but now height is controlled
                    pixels[y * buffer.stride + x] = 0xFFC5DA03;
                }
            }
        }

        ANativeWindow_unlockAndPost(window);
    }

    env->ReleaseFloatArrayElements(data, body, JNI_ABORT);
    ANativeWindow_release(window);
}

} // extern "C"

#include <jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>
#include <android/log.h>
#include <vector>
#include <algorithm>


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
            for (int x = 0; x < buffer.width; x++) {
                pixels[y * buffer.stride + x] = 0xFF121212;
            }
        }

        // Draw bars
        int displayBins = std::min(static_cast<int>(len), 128);
        float barWidth = static_cast<float>(buffer.width) / static_cast<float>(displayBins);

        for (int i = 0; i < displayBins; i++) {
            // Keep original order: bass (low freq) at left, treble (high freq) at right
            float val = body[i];
            float scaledVal = val * 5.0f;
            int barHeight = static_cast<int>(scaledVal * static_cast<float>(buffer.height));

            if (barHeight > buffer.height) barHeight = buffer.height;
            if (barHeight < 12) barHeight = 12; // Min height

            int startX = static_cast<int>(static_cast<float>(i) * barWidth);
            int endX = static_cast<int>(static_cast<float>(i + 1) * barWidth);
            int barBottom = buffer.height;
            int barTop = barBottom - barHeight;

            for (int x = startX; x <= endX; x++) {
                if (x < 0 || x >= buffer.width) continue;
                for (int y = barTop; y < barBottom; y++) {
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

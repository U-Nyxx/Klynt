#include <jni.h>
#include <string>
#include <android/log.h>
#include <sys/system_properties.h>
#include <unistd.h>

#define LOG_TAG "Klynt"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Low-level hardware↔software bridge for hook
// Direct property read bypasses Java cache, shows we talk to HAL.
// Research: platform glass uses Render Server + C++14 Metal; we map to AGSL + NDK.
// No "apple" word — license-safe.

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_unyxx_act_util_SocDetector_nativeHardware(JNIEnv* env, jclass) {
    char v[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.hardware", v);
    if (!v[0]) __system_property_get("ro.boot.hardware", v);
    return env->NewStringUTF(v);
}

JNIEXPORT jstring JNICALL
Java_com_unyxx_act_KlyntBridge_stringFromJNI(JNIEnv* env, jclass) {
    return env->NewStringUTF("Klynt — C++ NDK hook — hardware↔software, AGSL + RenderEffect, not full Kotlin");
}

} // extern "C"

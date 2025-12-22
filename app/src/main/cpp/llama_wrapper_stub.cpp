#include <jni.h>

extern "C" JNIEXPORT jlong JNICALL
Java_com_persianai_assistant_offline_LocalLlamaRunner_nativeLoad(JNIEnv *, jobject, jstring) {
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_persianai_assistant_offline_LocalLlamaRunner_nativeIsRealBackend(JNIEnv *, jobject) {
    return 0; // stub indicates real llama.cpp is not available
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_persianai_assistant_offline_LocalLlamaRunner_nativeInfer(JNIEnv *env, jobject, jlong, jstring, jint) {
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_persianai_assistant_offline_LocalLlamaRunner_nativeUnload(JNIEnv *, jobject, jlong) {
}

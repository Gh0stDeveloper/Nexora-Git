#include "nexora/syntax_engine.h"

#include <jni.h>

#include <exception>
#include <string>

namespace {

std::string from_jstring(JNIEnv* env, jstring value) {
    if (value == nullptr) return "";
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return "";
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring to_jstring(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

void throw_runtime(JNIEnv* env, const std::string& message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) {
        env->ThrowNew(type, message.c_str());
        env->DeleteLocalRef(type);
    }
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_editor_NativeSyntaxBridge_nativeVersion(
    JNIEnv* env,
    jobject
) {
    try {
        return to_jstring(env, nexora::syntax::version());
    } catch (const std::exception& error) {
        throw_runtime(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_editor_NativeSyntaxBridge_nativeAnalyze(
    JNIEnv* env,
    jobject,
    jstring grammar,
    jstring source
) {
    try {
        return to_jstring(
            env,
            nexora::syntax::analyze(
                from_jstring(env, grammar),
                from_jstring(env, source)
            )
        );
    } catch (const std::exception& error) {
        throw_runtime(env, error.what());
        return nullptr;
    }
}

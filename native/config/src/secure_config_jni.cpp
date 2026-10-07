#include <jni.h>

#include <algorithm>
#include <array>
#include <string>

#include "nexora_secure_config.generated.hpp"

namespace {

void SecureErase(std::string& value) {
    volatile char* data = value.empty() ? nullptr : value.data();
    for (std::size_t i = 0; i < value.size(); ++i) {
        data[i] = '\0';
    }
    value.clear();
}

jobjectArray ReadConfig(JNIEnv* env, jobject /* thiz */) {
    std::array<std::string, 4> values{
        nexora::secure_config::GithubClientId(),
        nexora::secure_config::BrokerBaseUrl(),
        nexora::secure_config::GithubCallbackUrl(),
        nexora::secure_config::AppCallbackUri(),
    };

    jclass string_class = env->FindClass("java/lang/String");
    if (string_class == nullptr) {
        for (auto& value : values) {
            SecureErase(value);
        }
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(
        static_cast<jsize>(values.size()),
        string_class,
        nullptr
    );
    if (result == nullptr) {
        env->DeleteLocalRef(string_class);
        for (auto& value : values) {
            SecureErase(value);
        }
        return nullptr;
    }

    for (std::size_t index = 0; index < values.size(); ++index) {
        jstring item = env->NewStringUTF(values[index].c_str());
        if (item == nullptr) {
            env->DeleteLocalRef(string_class);
            for (auto& value : values) {
                SecureErase(value);
            }
            return nullptr;
        }
        env->SetObjectArrayElement(result, static_cast<jsize>(index), item);
        env->DeleteLocalRef(item);
        SecureErase(values[index]);
    }

    env->DeleteLocalRef(string_class);
    return result;
}

constexpr JNINativeMethod kMethods[] = {
    {
        const_cast<char*>("readConfig"),
        const_cast<char*>("()[Ljava/lang/String;"),
        reinterpret_cast<void*>(ReadConfig),
    },
};

}  // namespace

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK ||
        env == nullptr) {
        return JNI_ERR;
    }

    jclass config_class = env->FindClass(
        "com/nexora/git/core/auth/SecureRuntimeConfigNative"
    );
    if (config_class == nullptr) {
        return JNI_ERR;
    }

    const jint result = env->RegisterNatives(
        config_class,
        kMethods,
        static_cast<jint>(std::size(kMethods))
    );
    env->DeleteLocalRef(config_class);

    return result == JNI_OK ? JNI_VERSION_1_6 : JNI_ERR;
}

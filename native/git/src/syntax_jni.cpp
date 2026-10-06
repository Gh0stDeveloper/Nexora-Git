#include "nexora/syntax_engine.h"

#include <jni.h>

#include <cstdint>
#include <exception>
#include <string>
#include <vector>

namespace {

constexpr uint32_t kReplacement = 0xFFFD;

void append_utf8(
    std::string& output,
    uint32_t codepoint
) {
    if (codepoint <= 0x7F) {
        output.push_back(static_cast<char>(codepoint));
    } else if (codepoint <= 0x7FF) {
        output.push_back(
            static_cast<char>(0xC0 | (codepoint >> 6))
        );
        output.push_back(
            static_cast<char>(0x80 | (codepoint & 0x3F))
        );
    } else if (codepoint <= 0xFFFF) {
        output.push_back(
            static_cast<char>(0xE0 | (codepoint >> 12))
        );
        output.push_back(
            static_cast<char>(
                0x80 | ((codepoint >> 6) & 0x3F)
            )
        );
        output.push_back(
            static_cast<char>(0x80 | (codepoint & 0x3F))
        );
    } else {
        output.push_back(
            static_cast<char>(0xF0 | (codepoint >> 18))
        );
        output.push_back(
            static_cast<char>(
                0x80 | ((codepoint >> 12) & 0x3F)
            )
        );
        output.push_back(
            static_cast<char>(
                0x80 | ((codepoint >> 6) & 0x3F)
            )
        );
        output.push_back(
            static_cast<char>(0x80 | (codepoint & 0x3F))
        );
    }
}

std::string from_jstring(
    JNIEnv* env,
    jstring value
) {
    if (value == nullptr) return "";

    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return "";

    std::string output;
    output.reserve(static_cast<size_t>(length) * 2);
    for (jsize index = 0; index < length; ++index) {
        uint32_t codepoint = chars[index];

        if (codepoint >= 0xD800 && codepoint <= 0xDBFF) {
            if (index + 1 < length) {
                const uint32_t low = chars[index + 1];
                if (low >= 0xDC00 && low <= 0xDFFF) {
                    codepoint =
                        0x10000 +
                        ((codepoint - 0xD800) << 10) +
                        (low - 0xDC00);
                    ++index;
                } else {
                    codepoint = kReplacement;
                }
            } else {
                codepoint = kReplacement;
            }
        } else if (
            codepoint >= 0xDC00 &&
            codepoint <= 0xDFFF
        ) {
            codepoint = kReplacement;
        }

        append_utf8(output, codepoint);
    }

    env->ReleaseStringChars(value, chars);
    return output;
}

bool continuation(unsigned char value) {
    return (value & 0xC0) == 0x80;
}

std::vector<jchar> utf8_to_utf16(
    const std::string& value
) {
    std::vector<jchar> output;
    output.reserve(value.size());

    size_t index = 0;
    while (index < value.size()) {
        const unsigned char first =
            static_cast<unsigned char>(value[index]);
        uint32_t codepoint = kReplacement;
        size_t length = 1;

        if (first <= 0x7F) {
            codepoint = first;
        } else if (
            first >= 0xC2 &&
            first <= 0xDF &&
            index + 1 < value.size() &&
            continuation(
                static_cast<unsigned char>(value[index + 1])
            )
        ) {
            codepoint =
                ((first & 0x1F) << 6) |
                (
                    static_cast<unsigned char>(
                        value[index + 1]
                    ) &
                    0x3F
                );
            length = 2;
        } else if (
            first >= 0xE0 &&
            first <= 0xEF &&
            index + 2 < value.size()
        ) {
            const unsigned char second =
                static_cast<unsigned char>(value[index + 1]);
            const unsigned char third =
                static_cast<unsigned char>(value[index + 2]);
            if (
                continuation(second) &&
                continuation(third)
            ) {
                const uint32_t candidate =
                    ((first & 0x0F) << 12) |
                    ((second & 0x3F) << 6) |
                    (third & 0x3F);
                if (
                    candidate >= 0x800 &&
                    !(candidate >= 0xD800 &&
                        candidate <= 0xDFFF)
                ) {
                    codepoint = candidate;
                    length = 3;
                }
            }
        } else if (
            first >= 0xF0 &&
            first <= 0xF4 &&
            index + 3 < value.size()
        ) {
            const unsigned char second =
                static_cast<unsigned char>(value[index + 1]);
            const unsigned char third =
                static_cast<unsigned char>(value[index + 2]);
            const unsigned char fourth =
                static_cast<unsigned char>(value[index + 3]);
            if (
                continuation(second) &&
                continuation(third) &&
                continuation(fourth)
            ) {
                const uint32_t candidate =
                    ((first & 0x07) << 18) |
                    ((second & 0x3F) << 12) |
                    ((third & 0x3F) << 6) |
                    (fourth & 0x3F);
                if (
                    candidate >= 0x10000 &&
                    candidate <= 0x10FFFF
                ) {
                    codepoint = candidate;
                    length = 4;
                }
            }
        }

        if (codepoint <= 0xFFFF) {
            output.push_back(
                static_cast<jchar>(codepoint)
            );
        } else {
            const uint32_t adjusted = codepoint - 0x10000;
            output.push_back(
                static_cast<jchar>(
                    0xD800 + (adjusted >> 10)
                )
            );
            output.push_back(
                static_cast<jchar>(
                    0xDC00 + (adjusted & 0x3FF)
                )
            );
        }

        index += length;
    }

    return output;
}

jstring to_jstring(
    JNIEnv* env,
    const std::string& value
) {
    const std::vector<jchar> utf16 =
        utf8_to_utf16(value);
    return env->NewString(
        utf16.empty() ? nullptr : utf16.data(),
        static_cast<jsize>(utf16.size())
    );
}

void throw_runtime(
    JNIEnv* env,
    const std::string& message
) {
    jclass type = env->FindClass(
        "java/lang/IllegalStateException"
    );
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

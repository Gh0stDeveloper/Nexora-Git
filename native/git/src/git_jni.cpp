#include "nexora/git_core.h"

#include <jni.h>

#include <exception>
#include <string>
#include <vector>

namespace {

std::string from_jstring(JNIEnv* env, jstring value) {
    if (value == nullptr) return "";

    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return "";

    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::vector<std::string> from_string_array(
    JNIEnv* env,
    jobjectArray values
) {
    std::vector<std::string> result;
    if (values == nullptr) return result;

    const jsize size = env->GetArrayLength(values);
    result.reserve(static_cast<size_t>(size));

    for (jsize index = 0; index < size; ++index) {
        auto item = static_cast<jstring>(
            env->GetObjectArrayElement(values, index)
        );
        result.push_back(from_jstring(env, item));
        if (item != nullptr) env->DeleteLocalRef(item);
    }

    return result;
}

nexora::git::Credentials credentials(
    JNIEnv* env,
    jstring username,
    jstring password
) {
    return {
        from_jstring(env, username),
        from_jstring(env, password),
    };
}

nexora::git::Author author(
    JNIEnv* env,
    jstring name,
    jstring email
) {
    return {
        from_jstring(env, name),
        from_jstring(env, email),
    };
}

void throw_native(
    JNIEnv* env,
    int code,
    int error_class,
    const std::string& message
) {
    jclass type = env->FindClass(
        "com/nexora/git/core/git/GitNativeException"
    );
    if (type == nullptr) {
        env->ExceptionClear();
        jclass runtime = env->FindClass("java/lang/RuntimeException");
        if (runtime != nullptr) {
            env->ThrowNew(runtime, message.c_str());
            env->DeleteLocalRef(runtime);
        }
        return;
    }

    jmethodID constructor = env->GetMethodID(
        type,
        "<init>",
        "(IILjava/lang/String;)V"
    );

    if (constructor == nullptr) {
        env->ExceptionClear();
        env->ThrowNew(type, message.c_str());
        env->DeleteLocalRef(type);
        return;
    }

    jstring text = env->NewStringUTF(message.c_str());
    jobject exception = env->NewObject(
        type,
        constructor,
        code,
        error_class,
        text
    );

    if (exception != nullptr) {
        env->Throw(static_cast<jthrowable>(exception));
        env->DeleteLocalRef(exception);
    }

    if (text != nullptr) env->DeleteLocalRef(text);
    env->DeleteLocalRef(type);
}

template <typename F>
jstring string_call(JNIEnv* env, F&& operation) {
    try {
        const std::string result = operation();
        return env->NewStringUTF(result.c_str());
    } catch (const nexora::git::GitError& error) {
        throw_native(
            env,
            error.code(),
            error.klass(),
            error.what()
        );
    } catch (const std::exception& error) {
        throw_native(env, -1, 0, error.what());
    } catch (...) {
        throw_native(env, -1, 0, "Unknown native Git error");
    }

    return nullptr;
}

template <typename F>
void void_call(JNIEnv* env, F&& operation) {
    try {
        operation();
    } catch (const nexora::git::GitError& error) {
        throw_native(
            env,
            error.code(),
            error.klass(),
            error.what()
        );
    } catch (const std::exception& error) {
        throw_native(env, -1, 0, error.what());
    } catch (...) {
        throw_native(env, -1, 0, "Unknown native Git error");
    }
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeInitialize(
    JNIEnv* env,
    jobject,
    jstring home_directory,
    jstring certificate_directory
) {
    void_call(env, [&]() {
        nexora::git::initialize(
            from_jstring(env, home_directory),
            from_jstring(env, certificate_directory)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeVersion(
    JNIEnv* env,
    jobject
) {
    return string_call(env, []() {
        return nexora::git::version();
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeInitRepository(
    JNIEnv* env,
    jobject,
    jstring path
) {
    return string_call(env, [&]() {
        return nexora::git::init_repository(
            from_jstring(env, path)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeClone(
    JNIEnv* env,
    jobject,
    jstring url,
    jstring destination,
    jstring username,
    jstring password
) {
    return string_call(env, [&]() {
        return nexora::git::clone_repository(
            from_jstring(env, url),
            from_jstring(env, destination),
            credentials(env, username, password)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeRemoteUrl(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring remote
) {
    return string_call(env, [&]() {
        return nexora::git::remote_url(
            from_jstring(env, repository_path),
            from_jstring(env, remote)
        );
    });
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeRemotes(
    JNIEnv* env,
    jobject,
    jstring repository_path
) {
    return string_call(env, [&]() {
        return nexora::git::remotes(
            from_jstring(env, repository_path)
        );
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeAddRemote(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring name,
    jstring url
) {
    void_call(env, [&]() {
        nexora::git::add_remote(
            from_jstring(env, repository_path),
            from_jstring(env, name),
            from_jstring(env, url)
        );
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeRenameRemote(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring old_name,
    jstring new_name
) {
    void_call(env, [&]() {
        nexora::git::rename_remote(
            from_jstring(env, repository_path),
            from_jstring(env, old_name),
            from_jstring(env, new_name)
        );
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeRemoveRemote(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring name
) {
    void_call(env, [&]() {
        nexora::git::remove_remote(
            from_jstring(env, repository_path),
            from_jstring(env, name)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeStatus(
    JNIEnv* env,
    jobject,
    jstring repository_path
) {
    return string_call(env, [&]() {
        return nexora::git::status(
            from_jstring(env, repository_path)
        );
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeStage(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jobjectArray paths
) {
    void_call(env, [&]() {
        nexora::git::stage(
            from_jstring(env, repository_path),
            from_string_array(env, paths)
        );
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeUnstage(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jobjectArray paths
) {
    void_call(env, [&]() {
        nexora::git::unstage(
            from_jstring(env, repository_path),
            from_string_array(env, paths)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeCommit(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring message,
    jstring author_name,
    jstring author_email
) {
    return string_call(env, [&]() {
        return nexora::git::commit(
            from_jstring(env, repository_path),
            from_jstring(env, message),
            author(env, author_name, author_email)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeBranches(
    JNIEnv* env,
    jobject,
    jstring repository_path
) {
    return string_call(env, [&]() {
        return nexora::git::branches(
            from_jstring(env, repository_path)
        );
    });
}


extern "C" JNIEXPORT void JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeSetUpstream(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring branch,
    jstring upstream
) {
    void_call(env, [&]() {
        nexora::git::set_upstream(
            from_jstring(env, repository_path),
            from_jstring(env, branch),
            from_jstring(env, upstream)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeDivergence(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring local_ref,
    jstring upstream_ref
) {
    return string_call(env, [&]() {
        return nexora::git::divergence(
            from_jstring(env, repository_path),
            from_jstring(env, local_ref),
            from_jstring(env, upstream_ref)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeRepositoryState(
    JNIEnv* env,
    jobject,
    jstring repository_path
) {
    return string_call(env, [&]() {
        return nexora::git::repository_state(
            from_jstring(env, repository_path)
        );
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeCreateBranch(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring name,
    jstring start_point
) {
    void_call(env, [&]() {
        nexora::git::create_branch(
            from_jstring(env, repository_path),
            from_jstring(env, name),
            from_jstring(env, start_point)
        );
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeCheckout(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring ref
) {
    void_call(env, [&]() {
        nexora::git::checkout(
            from_jstring(env, repository_path),
            from_jstring(env, ref)
        );
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeFetch(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring remote,
    jstring username,
    jstring password
) {
    void_call(env, [&]() {
        nexora::git::fetch(
            from_jstring(env, repository_path),
            from_jstring(env, remote),
            credentials(env, username, password)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativePull(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring remote,
    jstring strategy,
    jstring author_name,
    jstring author_email,
    jstring username,
    jstring password
) {
    return string_call(env, [&]() {
        return nexora::git::pull_with_strategy(
            from_jstring(env, repository_path),
            from_jstring(env, remote),
            from_jstring(env, strategy),
            author(env, author_name, author_email),
            credentials(env, username, password)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeContinueMerge(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring author_name,
    jstring author_email
) {
    return string_call(env, [&]() {
        return nexora::git::continue_merge(
            from_jstring(env, repository_path),
            author(env, author_name, author_email)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeContinueRebase(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring author_name,
    jstring author_email
) {
    return string_call(env, [&]() {
        return nexora::git::continue_rebase(
            from_jstring(env, repository_path),
            author(env, author_name, author_email)
        );
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeAbortRebase(
    JNIEnv* env,
    jobject,
    jstring repository_path
) {
    void_call(env, [&]() {
        nexora::git::abort_rebase(
            from_jstring(env, repository_path)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativePush(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring remote,
    jstring refspec,
    jstring username,
    jstring password
) {
    return string_call(env, [&]() {
        return nexora::git::push(
            from_jstring(env, repository_path),
            from_jstring(env, remote),
            from_jstring(env, refspec),
            credentials(env, username, password)
        );
    });
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativePushForceWithLease(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring remote,
    jstring refspec,
    jstring expected_remote_oid,
    jstring username,
    jstring password
) {
    return string_call(env, [&]() {
        return nexora::git::push_force_with_lease(
            from_jstring(env, repository_path),
            from_jstring(env, remote),
            from_jstring(env, refspec),
            from_jstring(env, expected_remote_oid),
            credentials(env, username, password)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeDiff(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring mode,
    jstring relative_path
) {
    return string_call(env, [&]() {
        return nexora::git::diff(
            from_jstring(env, repository_path),
            from_jstring(env, mode),
            from_jstring(env, relative_path)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeMerge(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring ref,
    jstring author_name,
    jstring author_email
) {
    return string_call(env, [&]() {
        return nexora::git::merge(
            from_jstring(env, repository_path),
            from_jstring(env, ref),
            author(env, author_name, author_email)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeConflicts(
    JNIEnv* env,
    jobject,
    jstring repository_path
) {
    return string_call(env, [&]() {
        return nexora::git::conflicts(
            from_jstring(env, repository_path)
        );
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeResolveConflict(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring path,
    jstring resolution
) {
    void_call(env, [&]() {
        nexora::git::resolve_conflict(
            from_jstring(env, repository_path),
            from_jstring(env, path),
            from_jstring(env, resolution)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeHistory(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring relative_path,
    jint limit
) {
    return string_call(env, [&]() {
        return nexora::git::history(
            from_jstring(env, repository_path),
            from_jstring(env, relative_path),
            static_cast<int>(limit)
        );
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nexora_git_core_git_NativeGitBridge_nativeBlame(
    JNIEnv* env,
    jobject,
    jstring repository_path,
    jstring relative_path
) {
    return string_call(env, [&]() {
        return nexora::git::blame(
            from_jstring(env, repository_path),
            from_jstring(env, relative_path)
        );
    });
}

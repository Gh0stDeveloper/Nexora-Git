# Nexora Git release shrinker rules.
#
# Library-specific consumer rules from Retrofit, OkHttp, Room and Hilt are
# applied automatically. Add explicit rules here only when a concrete feature
# requires them so the release build remains auditable.


# JNI entrypoints and native exception constructor are referenced from C++ by
# their binary names. Preserve them in release builds.
-keepclasseswithmembernames,includedescriptorclasses class com.nexora.git.core.git.NativeGitBridge {
    native <methods>;
}

-keep class com.nexora.git.core.git.GitNativeException {
    <init>(int, int, java.lang.String);
}

-keepclasseswithmembernames,includedescriptorclasses class com.nexora.git.core.editor.NativeSyntaxBridge {
    native <methods>;
}


# The secure runtime configuration class is resolved by libnexoraconfig.so
# through JNI RegisterNatives. Keep its binary name and native method name.
-keep,allowoptimization class com.nexora.git.core.auth.SecureRuntimeConfigNative {
    native <methods>;
}

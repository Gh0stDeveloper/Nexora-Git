plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val githubClientId = providers.gradleProperty("nexora.githubClientId")
    .orElse(providers.environmentVariable("NEXORA_GITHUB_CLIENT_ID"))
    .getOrElse("")

val authBrokerBaseUrl = providers.gradleProperty("nexora.authBrokerBaseUrl")
    .orElse(providers.environmentVariable("NEXORA_AUTH_BROKER_BASE_URL"))
    .getOrElse("")

val githubCallbackUrl = providers.gradleProperty("nexora.githubCallbackUrl")
    .orElse(providers.environmentVariable("NEXORA_GITHUB_CALLBACK_URL"))
    .getOrElse("")

val releaseStoreFile = providers
    .gradleProperty("nexora.signing.storeFile")
    .orElse(providers.environmentVariable("NEXORA_SIGNING_STORE_FILE"))
    .orNull

val releaseStorePassword = providers
    .gradleProperty("nexora.signing.storePassword")
    .orElse(providers.environmentVariable("NEXORA_SIGNING_STORE_PASSWORD"))
    .orNull

val releaseKeyAlias = providers
    .gradleProperty("nexora.signing.keyAlias")
    .orElse(providers.environmentVariable("NEXORA_SIGNING_KEY_ALIAS"))
    .orNull

val releaseKeyPassword = providers
    .gradleProperty("nexora.signing.keyPassword")
    .orElse(providers.environmentVariable("NEXORA_SIGNING_KEY_PASSWORD"))
    .orNull

val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.nexora.git"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.nexora.git"
        minSdk = 26
        targetSdk = 36
        versionCode = 10000
        versionName = "1.0.0"

        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$githubClientId\"")
        buildConfigField("String", "AUTH_BROKER_BASE_URL", "\"$authBrokerBaseUrl\"")
        buildConfigField("String", "GITHUB_CALLBACK_URL", "\"$githubCallbackUrl\"")
        buildConfigField("String", "APP_CALLBACK_URI", "\"nexoragit://oauth/callback\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += setOf(
                "arm64-v8a",
                "armeabi-v7a",
                "x86_64",
            )
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DNEXORA_LIBGIT2_REF=49e408b3208bc3093757a1c2db938d3590f3f412",
                    "-DNEXORA_MBEDTLS_REF=068ff080b369adfac81509f9b57b2afabaf82dc5",
                )
                cppFlags += listOf(
                    "-std=c++17",
                    "-fexceptions",
                    "-frtti",
                )
            }
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    ndkVersion = "27.2.12479018"

    externalNativeBuild {
        cmake {
            path = file("../native/git/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

hilt {
    enableAggregatingTask = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.browser)

    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit4)
    testImplementation(libs.org.json)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

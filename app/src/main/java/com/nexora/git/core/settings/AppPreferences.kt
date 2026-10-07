package com.nexora.git.core.settings

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
}

enum class AppLanguage(
    val languageTag: String?,
) {
    SYSTEM(null),
    ENGLISH("en"),
    SPANISH("es-MX"),
}

data class ProductPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val confirmForcePush: Boolean = true,
    val wifiOnlyLargeTransfers: Boolean = false,
)

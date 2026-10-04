package com.nexora.git.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NexoraTypography = Typography(
    headlineSmall = Typography().headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = Typography().titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = Typography().titleMedium.copy(
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = Typography().bodyLarge.copy(
        lineHeight = 22.sp,
    ),
    labelLarge = Typography().labelLarge.copy(
        fontWeight = FontWeight.Medium,
    ),
)

val CodeFontFamily: FontFamily = FontFamily.Monospace

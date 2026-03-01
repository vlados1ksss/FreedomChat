package com.vladdev.freedomchat.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vladdev.freedomchat.R

val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex)
)

val AppTypography = Typography().run {

    copy(

        // ===== DISPLAY =====
        displayLarge = displayLarge.copy(
            fontFamily = GoogleSansFlex
        ),
        displayMedium = displayMedium.copy(
            fontFamily = GoogleSansFlex
        ),
        displaySmall = displaySmall.copy(
            fontFamily = GoogleSansFlex
        ),

        // ===== HEADLINES =====
        headlineLarge = headlineLarge.copy(
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.Bold
        ),

        headlineMedium = headlineMedium.copy(
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        ),

        headlineSmall = headlineSmall.copy(
            fontFamily = GoogleSansFlex
        ),

        // ===== TITLES =====
        titleLarge = titleLarge.copy(
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.SemiBold
        ),

        titleMedium = titleMedium.copy(
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.SemiBold
        ),

        titleSmall = titleSmall.copy(
            fontFamily = GoogleSansFlex
        ),

        // ===== BODY =====
        bodyLarge = bodyLarge.copy(
            fontFamily = GoogleSansFlex,
            lineHeight = 22.sp
        ),

        bodyMedium = bodyMedium.copy(
            fontFamily = GoogleSansFlex
        ),

        bodySmall = bodySmall.copy(
            fontFamily = GoogleSansFlex
        ),

        // ===== LABELS =====
        labelLarge = labelLarge.copy(
            fontFamily = GoogleSansFlex
        ),

        labelMedium = labelMedium.copy(
            fontFamily = GoogleSansFlex
        ),

        labelSmall = labelSmall.copy(
            fontFamily = GoogleSansFlex,
            letterSpacing = 0.sp
        ),
    )
}



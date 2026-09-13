package com.kin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

data class KinSkinTokens(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val border: Color,
    val error: Color,
)

fun kinSkinTokens(skinId: String): KinSkinTokens = when (skinId) {
    "midnight" -> KinSkinTokens(
        background = Color(0xFF09080D),
        surface = Color(0xFF15131B),
        surfaceVariant = Color(0xFF211D29),
        textPrimary = Color(0xFFFFF7FF),
        textSecondary = Color(0xFFD7CBDD),
        textMuted = Color(0xFFA99DAF),
        accent = Color(0xFFC5A8FF),
        border = Color(0xFF3D3546),
        error = Color(0xFFFFB4AB),
    )
    "y2k" -> KinSkinTokens(
        background = Color(0xFFFFF4E6),
        surface = Color(0xFFFFFBF6),
        surfaceVariant = Color(0xFFF6E8FF),
        textPrimary = Color(0xFF2C1B28),
        textSecondary = Color(0xFF665361),
        textMuted = Color(0xFF8A7683),
        accent = Color(0xFF8A3FFC),
        border = Color(0xFFE1BAD8),
        error = Color(0xFFBA1A1A),
    )
    else -> KinSkinTokens(
        background = Color(0xFFFFFBF7),
        surface = Color(0xFFFFFCFA),
        surfaceVariant = Color(0xFFF3EDF6),
        textPrimary = Color(0xFF241F27),
        textSecondary = Color(0xFF5E5661),
        textMuted = Color(0xFF817884),
        accent = Color(0xFF7655C8),
        border = Color(0xFFDDD4E2),
        error = Color(0xFFBA1A1A),
    )
}

fun kinColorScheme(skinId: String): ColorScheme {
    val tokens = kinSkinTokens(skinId)
    return if (skinId == "midnight") {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = Color(0xFF25133F),
            primaryContainer = Color(0xFF3B2850),
            onPrimaryContainer = tokens.textPrimary,
            secondary = Color(0xFFFF9FC8),
            onSecondary = Color(0xFF5D1135),
            secondaryContainer = Color(0xFF3C2734),
            onSecondaryContainer = Color(0xFFFFE7F2),
            background = tokens.background,
            onBackground = tokens.textPrimary,
            surface = tokens.surface,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surfaceVariant,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.border,
            outlineVariant = tokens.border.copy(alpha = 0.65f),
            error = tokens.error,
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        val secondary = if (skinId == "y2k") Color(0xFFE83EA8) else Color(0xFFB45C7E)
        val secondaryContainer = if (skinId == "y2k") Color(0xFFFFD7F0) else Color(0xFFFFE4EE)
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = Color.White,
            primaryContainer = if (skinId == "y2k") Color(0xFFE9D5FF) else Color(0xFFEDE3FF),
            onPrimaryContainer = if (skinId == "y2k") Color(0xFF35005D) else Color(0xFF26134E),
            secondary = secondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = if (skinId == "y2k") Color(0xFF550035) else Color(0xFF4A1730),
            tertiary = if (skinId == "y2k") Color(0xFF008D83) else Color(0xFF6F5B2F),
            background = tokens.background,
            onBackground = tokens.textPrimary,
            surface = tokens.surface,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surfaceVariant,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.border,
            outlineVariant = tokens.border.copy(alpha = 0.70f),
            error = tokens.error,
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    }
}

fun kinCardShape(skinId: String): Shape = when (skinId) {
    "midnight" -> RoundedCornerShape(16.dp)
    "y2k" -> RoundedCornerShape(8.dp)
    else -> RoundedCornerShape(22.dp)
}

fun kinMediaShape(skinId: String): Shape = when (skinId) {
    "midnight" -> RoundedCornerShape(12.dp)
    "y2k" -> RoundedCornerShape(4.dp)
    else -> RoundedCornerShape(18.dp)
}

fun kinBackgroundBrush(skinId: String): Brush = when (skinId) {
    "midnight" -> Brush.verticalGradient(
        listOf(Color(0xFF09080D), Color(0xFF121019), Color(0xFF1D1322)),
    )
    "y2k" -> Brush.verticalGradient(
        listOf(Color(0xFFFFF3DF), Color(0xFFF4E9FF), Color(0xFFFFE9F6)),
    )
    else -> Brush.verticalGradient(
        listOf(Color(0xFFFFFBF7), Color(0xFFF7F2FF), Color(0xFFFFF8FB)),
    )
}

fun kinSpaceHeroBrush(skinId: String): Brush = when (skinId) {
    "midnight" -> Brush.linearGradient(
        listOf(Color(0xFF160E28), Color(0xFF4A234E), Color(0xFF121A32)),
    )
    "y2k" -> Brush.linearGradient(
        listOf(Color(0xFFFFB5E8), Color(0xFFB8C0FF), Color(0xFFA8F0E5)),
    )
    else -> Brush.linearGradient(
        listOf(Color(0xFFE9DDFF), Color(0xFFFFE2EE), Color(0xFFFFF0D8)),
    )
}

@Composable
fun KinSkinBackdrop(
    skinId: String,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kinBackgroundBrush(skinId)),
        content = content,
    )
}

fun Modifier.kinY2kBorder(skinId: String): Modifier {
    val tokens = kinSkinTokens(skinId)
    return if (skinId == "y2k") border(1.dp, tokens.border, kinCardShape(skinId)) else this
}

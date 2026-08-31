package com.kin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

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

fun Modifier.kinY2kBorder(skinId: String): Modifier =
    if (skinId == "y2k") border(1.dp, Color(0x55FF2EB8), kinCardShape(skinId)) else this

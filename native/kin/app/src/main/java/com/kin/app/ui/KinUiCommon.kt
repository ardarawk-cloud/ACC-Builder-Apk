package com.kin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KinScreenColumn(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle)
        Spacer(Modifier.height(4.dp))
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun KinAudiencePicker(selected: String, onSelected: (String) -> Unit) {
    listOf("Public", "Friends", "Circle", "Only Me").chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = { Text(option) },
                )
            }
        }
    }
}

fun kinSkinLabel(id: String): String = when (id) {
    "midnight" -> "Midnight"
    "y2k" -> "Y2K"
    else -> "KIN Original"
}

fun kinFormatPostTime(createdAt: Long): String {
    val ageMs = System.currentTimeMillis() - createdAt
    if (ageMs in 0..59_999) return "now"
    if (ageMs in 60_000..3_599_999) return "${ageMs / 60_000}m"
    if (ageMs in 3_600_000..86_399_999) return "${ageMs / 3_600_000}h"
    return SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(createdAt))
}

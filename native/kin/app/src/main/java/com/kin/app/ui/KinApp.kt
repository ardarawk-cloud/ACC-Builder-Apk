package com.kin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kin.app.KinAppGraph
import com.kin.app.session.KinSession
import com.kin.app.share.KinShareInbox

enum class KinRoot(val label: String, val symbol: String) {
    HOME("HOME", "⌂"),
    PEOPLE("PEOPLE", "◎"),
    CREATE("+", "+"),
    CHAT("CHAT", "✉"),
    ME("ME", "●"),
}

private fun kinOriginalColors() = lightColorScheme(
    primary = Color(0xFF7C5CFC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE6FF),
    onPrimaryContainer = Color(0xFF25105A),
    secondary = Color(0xFFFF7FA6),
    secondaryContainer = Color(0xFFFFE3EC),
    background = Color(0xFFF9F7FF),
    surface = Color(0xFFFFFBFF),
)

private fun kinMidnightColors() = darkColorScheme(
    primary = Color(0xFFB6A5FF),
    onPrimary = Color(0xFF211947),
    primaryContainer = Color(0xFF3F365E),
    onPrimaryContainer = Color(0xFFF1ECFF),
    secondary = Color(0xFFFF9CC0),
    secondaryContainer = Color(0xFF382A33),
    onSecondaryContainer = Color(0xFFFFE7F1),
    background = Color(0xFF0E0D12),
    surface = Color(0xFF17151C),
)

private fun kinY2kColors() = lightColorScheme(
    primary = Color(0xFFFF2EB8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD4F1),
    onPrimaryContainer = Color(0xFF4C0037),
    secondary = Color(0xFF536DFE),
    secondaryContainer = Color(0xFFDDE2FF),
    onSecondaryContainer = Color(0xFF101A5C),
    tertiary = Color(0xFF00A99D),
    background = Color(0xFFFFF4D8),
    surface = Color(0xFFFFFDF7),
)

@Composable
fun KinApp(graph: KinAppGraph, session: KinSession) {
    var selected by rememberSaveable { mutableStateOf(KinRoot.HOME) }
    val profile by graph.profileRepository.observeProfile().collectAsStateWithLifecycle(initialValue = null)
    val sharedContent by KinShareInbox.sharedContent.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        graph.relationshipRepository.ensureStarterData()
    }

    LaunchedEffect(sharedContent) {
        if (sharedContent != null) selected = KinRoot.CREATE
    }

    val colorScheme = when (profile?.skinId) {
        "midnight" -> kinMidnightColors()
        "y2k" -> kinY2kColors()
        else -> kinOriginalColors()
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            topBar = {
                KinHeader(
                    selected = selected,
                    skinId = profile?.skinId ?: "kin-original",
                )
            },
            bottomBar = {
                NavigationBar {
                    KinRoot.entries.forEach { root ->
                        NavigationBarItem(
                            selected = selected == root,
                            onClick = { selected = root },
                            icon = { Text(root.symbol, fontWeight = FontWeight.Bold) },
                            label = { Text(root.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                color = MaterialTheme.colorScheme.background,
            ) {
                when (selected) {
                    KinRoot.HOME -> HomeScreen(
                        repository = graph.postRepository,
                        onCreate = { selected = KinRoot.CREATE },
                    )
                    KinRoot.PEOPLE -> PeopleScreen(graph.relationshipRepository)
                    KinRoot.CREATE -> ComposerScreen(
                        graph = graph,
                        session = session,
                        profile = profile,
                        onPublished = { selected = KinRoot.HOME },
                    )
                    KinRoot.CHAT -> ChatScreen(graph.relationshipRepository)
                    KinRoot.ME -> MeScreen(graph = graph, session = session)
                }
            }
        }
    }
}

@Composable
private fun KinHeader(selected: KinRoot, skinId: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("KIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Your Space. Your People.", style = MaterialTheme.typography.labelMedium)
            }
            Column {
                Text(selected.label, style = MaterialTheme.typography.labelLarge)
                Text(kinSkinLabel(skinId), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

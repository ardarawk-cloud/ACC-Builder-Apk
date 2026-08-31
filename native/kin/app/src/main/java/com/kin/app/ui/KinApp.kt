package com.kin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    primary = Color(0xFF7655C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE3FF),
    onPrimaryContainer = Color(0xFF26134E),
    secondary = Color(0xFFB45C7E),
    secondaryContainer = Color(0xFFFFE4EE),
    background = Color(0xFFFFFBF7),
    surface = Color(0xFFFFFCFA),
)

private fun kinMidnightColors() = darkColorScheme(
    primary = Color(0xFFC5A8FF),
    onPrimary = Color(0xFF25133F),
    primaryContainer = Color(0xFF3B2850),
    onPrimaryContainer = Color(0xFFF4ECFF),
    secondary = Color(0xFFFF9FC8),
    secondaryContainer = Color(0xFF3C2734),
    onSecondaryContainer = Color(0xFFFFE7F2),
    background = Color(0xFF09080D),
    surface = Color(0xFF15131B),
)

private fun kinY2kColors() = lightColorScheme(
    primary = Color(0xFF8A3FFC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9D5FF),
    onPrimaryContainer = Color(0xFF35005D),
    secondary = Color(0xFFE83EA8),
    secondaryContainer = Color(0xFFFFD7F0),
    onSecondaryContainer = Color(0xFF550035),
    tertiary = Color(0xFF008D83),
    background = Color(0xFFFFF4E6),
    surface = Color(0xFFFFFBF6),
)

private fun kinTypography(skinId: String): Typography {
    val base = Typography()
    return when (skinId) {
        "y2k" -> base.copy(
            headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
            headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
            headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
        )
        "midnight" -> base.copy(
            headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Black),
        )
        else -> base
    }
}

@Composable
fun KinApp(graph: KinAppGraph, session: KinSession) {
    var selected by rememberSaveable { mutableStateOf(KinRoot.HOME) }
    val profile by graph.profileRepository.observeProfile().collectAsStateWithLifecycle(initialValue = null)
    val sharedContent by KinShareInbox.sharedContent.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        graph.relationshipRepository.ensureStarterData()
        graph.relationshipRepository.syncConnections()
        graph.postRepository.refreshFeed()
    }

    LaunchedEffect(sharedContent) {
        if (sharedContent != null) selected = KinRoot.CREATE
    }

    val skinId = profile?.skinId ?: "kin-original"
    val colorScheme = when (skinId) {
        "midnight" -> kinMidnightColors()
        "y2k" -> kinY2kColors()
        else -> kinOriginalColors()
    }

    MaterialTheme(colorScheme = colorScheme, typography = kinTypography(skinId)) {
        KinSkinBackdrop(skinId = skinId) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                topBar = {
                    KinCompactHeader(selected = selected, skinId = skinId)
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    when (selected) {
                        KinRoot.HOME -> HomeScreen(
                            repository = graph.postRepository,
                            currentUsername = profile?.username ?: session.username,
                            skinId = skinId,
                            onCreate = { selected = KinRoot.CREATE },
                        )
                        KinRoot.PEOPLE -> PeopleV1BScreen(graph.relationshipRepository)
                        KinRoot.CREATE -> ComposerScreen(
                            graph = graph,
                            session = session,
                            profile = profile,
                            onPublished = { selected = KinRoot.HOME },
                        )
                        KinRoot.CHAT -> ChatScreen(
                            relationshipRepository = graph.relationshipRepository,
                            chatRepository = graph.chatRepository,
                        )
                        KinRoot.ME -> MeScreen(graph = graph, session = session)
                    }
                }
            }
        }
    }
}

@Composable
private fun KinCompactHeader(selected: KinRoot, skinId: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (skinId == "midnight") 0.88f else 0.94f),
        shadowElevation = if (skinId == "y2k") 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("KIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                when (selected) {
                    KinRoot.HOME -> "My People"
                    KinRoot.CREATE -> "New Post"
                    KinRoot.ME -> "My Space"
                    else -> selected.label.lowercase().replaceFirstChar { it.uppercase() }
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

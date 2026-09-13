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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
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
    val tokens = kinSkinTokens(skinId)

    MaterialTheme(colorScheme = kinColorScheme(skinId), typography = kinTypography(skinId)) {
        KinSkinBackdrop(skinId = skinId) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                topBar = {
                    KinCompactHeader(selected = selected, skinId = skinId)
                },
                bottomBar = {
                    NavigationBar(containerColor = tokens.surface.copy(alpha = 0.97f)) {
                        KinRoot.entries.forEach { root ->
                            NavigationBarItem(
                                selected = selected == root,
                                onClick = { selected = root },
                                icon = { Text(root.symbol, fontWeight = FontWeight.Bold) },
                                label = { Text(root.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = tokens.accent,
                                    selectedTextColor = tokens.textPrimary,
                                    unselectedIconColor = tokens.textMuted,
                                    unselectedTextColor = tokens.textMuted,
                                    indicatorColor = tokens.surfaceVariant,
                                ),
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
    val tokens = kinSkinTokens(skinId)
    Surface(
        color = tokens.surface.copy(alpha = if (skinId == "midnight") 0.92f else 0.96f),
        shadowElevation = if (skinId == "y2k") 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "KIN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = tokens.textPrimary,
            )
            Text(
                when (selected) {
                    KinRoot.HOME -> "My People"
                    KinRoot.CREATE -> "New Post"
                    KinRoot.ME -> "My Space"
                    else -> selected.label.lowercase().replaceFirstChar { it.uppercase() }
                },
                style = MaterialTheme.typography.labelLarge,
                color = tokens.textSecondary,
            )
        }
    }
}

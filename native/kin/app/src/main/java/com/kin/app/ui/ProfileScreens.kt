package com.kin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kin.app.KinAppGraph
import com.kin.app.auth.KinAuthResult
import com.kin.app.auth.KinProfileUpdate
import com.kin.app.data.KinCircleEntity
import com.kin.app.data.KinPersonWithCircles
import com.kin.app.data.KinProfileEntity
import com.kin.app.data.KinRelationshipRepository
import com.kin.app.session.KinSession
import kotlinx.coroutines.launch

private enum class MeRoute {
    ROOT,
    RELATIONSHIPS,
    CUSTOMIZE,
}

@Composable
fun MeScreen(graph: KinAppGraph, session: KinSession) {
    val profile by graph.profileRepository.observeProfile().collectAsStateWithLifecycle(initialValue = null)
    var route by rememberSaveable { mutableStateOf(MeRoute.ROOT) }
    val scope = rememberCoroutineScope()

    when (route) {
        MeRoute.CUSTOMIZE -> {
            CustomizeSpaceScreen(
                profile = profile,
                graph = graph,
                onBack = { route = MeRoute.ROOT },
            )
            return
        }
        MeRoute.RELATIONSHIPS -> {
            RelationshipManagerScreen(
                repository = graph.relationshipRepository,
                onBack = { route = MeRoute.ROOT },
            )
            return
        }
        MeRoute.ROOT -> Unit
    }

    KinScreenColumn("My Space", "Your profile is your room on KIN.") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    profile?.displayName ?: session.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("@${profile?.username ?: session.username}")
                profile?.bio?.takeIf { it.isNotBlank() }?.let { Text(it) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Skin · ${kinSkinLabel(profile?.skinId ?: "kin-original")}",
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Button(onClick = { route = MeRoute.CUSTOMIZE }, modifier = Modifier.fillMaxWidth()) {
            Text("Customize Space")
        }
        OutlinedButton(onClick = { route = MeRoute.RELATIONSHIPS }, modifier = Modifier.fillMaxWidth()) {
            Text("Circles & Private Notes")
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Guestbook", fontWeight = FontWeight.Bold)
                Text("Empty for now. Real profile messages will live here after friend sync is online.")
            }
        }

        OutlinedButton(
            onClick = { scope.launch { graph.authRepository.logout() } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Log out") }
    }
}

@Composable
private fun CustomizeSpaceScreen(
    profile: KinProfileEntity?,
    graph: KinAppGraph,
    onBack: () -> Unit,
) {
    var bio by rememberSaveable { mutableStateOf(profile?.bio.orEmpty()) }
    var savedMessage by rememberSaveable { mutableStateOf("") }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(profile?.bio) {
        profile?.let { bio = it.bio }
    }

    KinScreenColumn(
        title = "Customize Space",
        subtitle = "One skin setting controls the real KIN theme. No hidden second theme anymore.",
    ) {
        OutlinedButton(onClick = onBack) { Text("← My Space") }

        Text("Skin", fontWeight = FontWeight.Bold)
        listOf(
            "kin-original" to "KIN Original",
            "midnight" to "Midnight",
            "y2k" to "Y2K",
        ).forEach { (id, label) ->
            FilterChip(
                selected = profile?.skinId == id,
                onClick = {
                    if (profile == null) return@FilterChip
                    scope.launch {
                        when (val result = graph.authRepository.updateProfile(KinProfileUpdate(skinId = id))) {
                            KinAuthResult.Success -> {
                                savedMessage = "$label applied"
                                saveFailed = false
                            }
                            is KinAuthResult.Error -> {
                                savedMessage = result.message
                                saveFailed = true
                            }
                        }
                    }
                },
                label = { Text(if (profile?.skinId == id) "✓ $label" else label) },
            )
        }
        Text(
            "The header, background, cards and controls change immediately after you choose a skin.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it.take(160); savedMessage = ""; saveFailed = false },
            label = { Text("Short bio") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Button(
            onClick = {
                if (profile == null) return@Button
                scope.launch {
                    when (val result = graph.authRepository.updateProfile(KinProfileUpdate(bio = bio.trim()))) {
                        KinAuthResult.Success -> {
                            savedMessage = "Bio saved"
                            saveFailed = false
                        }
                        is KinAuthResult.Error -> {
                            savedMessage = result.message
                            saveFailed = true
                        }
                    }
                }
            },
            enabled = profile != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save bio") }

        if (savedMessage.isNotBlank()) {
            Text(
                savedMessage,
                color = if (saveFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Advanced skin editor", fontWeight = FontWeight.Bold)
                Text("Background images, card shapes, fonts and remix sharing come after the core social flow is stable.")
            }
        }
    }
}

@Composable
private fun RelationshipManagerScreen(
    repository: KinRelationshipRepository,
    onBack: () -> Unit,
) {
    val people by repository.observePeople().collectAsStateWithLifecycle(initialValue = emptyList())
    val circles by repository.observeCircles().collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text("← My Space") }
            Spacer(Modifier.height(10.dp))
            Text(
                "Circles & Private Notes",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("Only you see these relationship labels and notes.")
        }

        if (people.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Circle system is ready", fontWeight = FontWeight.Bold)
                        Text("When real connections arrive, assign each person to one or more Circles.")
                    }
                }
            }
            items(circles, key = { it.id }) { circle ->
                CircleInfoCard(circle)
            }
        } else {
            items(people, key = { it.person.id }) { person ->
                RelationshipCard(person = person, allCircles = circles, repository = repository)
            }
        }
    }
}

@Composable
private fun CircleInfoCard(circle: KinCircleEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(circle.name, fontWeight = FontWeight.Bold)
            Text("Private relationship label")
        }
    }
}

@Composable
private fun RelationshipCard(
    person: KinPersonWithCircles,
    allCircles: List<KinCircleEntity>,
    repository: KinRelationshipRepository,
) {
    var note by remember(person.person.id, person.person.privateNote) {
        mutableStateOf(person.person.privateNote)
    }
    val scope = rememberCoroutineScope()
    val selectedIds = person.circles.map { it.id }.toSet()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(person.person.displayName, fontWeight = FontWeight.Bold)
            Text(person.person.handle)

            allCircles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { circle ->
                        val selected = circle.id in selectedIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = if (selected) {
                                    selectedIds - circle.id
                                } else {
                                    selectedIds + circle.id
                                }
                                scope.launch {
                                    repository.setPersonCircles(person.person.id, next.toList())
                                }
                            },
                            label = { Text(circle.name) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                label = { Text("Private note") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Button(
                onClick = {
                    scope.launch {
                        repository.savePrivateNote(person.person.id, note.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save private note") }
        }
    }
}

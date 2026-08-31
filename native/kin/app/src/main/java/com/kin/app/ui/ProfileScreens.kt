package com.kin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.kin.app.data.kinPostMediaFromJson
import com.kin.app.session.KinSession
import kotlinx.coroutines.launch

private enum class MeRoute {
    ROOT,
    RELATIONSHIPS,
    CUSTOMIZE,
}

private enum class SpaceTab {
    JOURNAL,
    MEDIA,
}

@Composable
fun MeScreen(graph: KinAppGraph, session: KinSession) {
    val profile by graph.profileRepository.observeProfile().collectAsStateWithLifecycle(initialValue = null)
    val posts by graph.postRepository.observePosts().collectAsStateWithLifecycle(initialValue = emptyList())
    val people by graph.relationshipRepository.observePeople().collectAsStateWithLifecycle(initialValue = emptyList())
    var route by rememberSaveable { mutableStateOf(MeRoute.ROOT) }
    var tab by rememberSaveable { mutableStateOf(SpaceTab.JOURNAL) }
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

    val username = profile?.username ?: session.username
    val displayName = profile?.displayName ?: session.displayName
    val skinId = profile?.skinId ?: "kin-original"
    val ownPosts = posts.filter { it.authorUsername.equals(username, ignoreCase = true) }
    val mediaPosts = ownPosts.filter { kinPostMediaFromJson(it.mediaJson).isNotEmpty() }
    val highlight = ownPosts.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (skinId == "midnight") 250.dp else 230.dp)
                    .background(kinSpaceHeroBrush(skinId)),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(displayName.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        }
                    }
                    Text(displayName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("@$username", style = MaterialTheme.typography.bodyMedium)
                    profile?.bio?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text("${kinSkinLabel(skinId)} Space", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaceStat("Journal", ownPosts.size, Modifier.weight(1f), skinId)
                    SpaceStat("Media", mediaPosts.sumOf { kinPostMediaFromJson(it.mediaJson).size }, Modifier.weight(1f), skinId)
                    SpaceStat("People", people.size, Modifier.weight(1f), skinId)
                }
                Button(onClick = { route = MeRoute.CUSTOMIZE }, modifier = Modifier.fillMaxWidth()) {
                    Text("✦ Customize My Space")
                }
            }
        }

        if (highlight != null) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Latest highlight", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    KinFeedPostCard(post = highlight, skinId = skinId, mine = false, compact = true)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = tab == SpaceTab.JOURNAL,
                    onClick = { tab = SpaceTab.JOURNAL },
                    label = { Text("Journal") },
                )
                FilterChip(
                    selected = tab == SpaceTab.MEDIA,
                    onClick = { tab = SpaceTab.MEDIA },
                    label = { Text("Photo Wall · Video") },
                )
                OutlinedButton(onClick = { route = MeRoute.RELATIONSHIPS }) {
                    Text("People")
                }
            }
        }

        if (tab == SpaceTab.JOURNAL) {
            if (ownPosts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxWidth(),
                        shape = kinCardShape(skinId),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Your journal is empty", fontWeight = FontWeight.Bold)
                            Text("Moments you share will build your Space chronologically.")
                        }
                    }
                }
            } else {
                items(ownPosts, key = { "space-${it.id}" }) { post ->
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        KinFeedPostCard(post = post, skinId = skinId, mine = false, compact = true)
                    }
                }
            }
        } else {
            if (mediaPosts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxWidth(),
                        shape = kinCardShape(skinId),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Photo Wall", fontWeight = FontWeight.Bold)
                            Text("Your photos and videos will collect here automatically.")
                        }
                    }
                }
            } else {
                items(mediaPosts, key = { "media-${it.id}" }) { post ->
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(kinFormatPostTime(post.createdAt), style = MaterialTheme.typography.labelSmall)
                        KinPostMediaStrip(
                            media = kinPostMediaFromJson(post.mediaJson),
                            skinId = skinId,
                            compact = true,
                        )
                        post.text.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = kinCardShape(skinId),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Guestbook", fontWeight = FontWeight.Bold)
                        Text("Reserved for messages from your people. It is not a public comment wall.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(
                    onClick = { scope.launch { graph.authRepository.logout() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Log out") }
            }
        }
    }
}

@Composable
private fun SpaceStat(label: String, count: Int, modifier: Modifier, skinId: String) {
    Card(
        modifier = modifier.kinY2kBorder(skinId),
        shape = kinCardShape(skinId),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
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
    val activeSkin = profile?.skinId ?: "kin-original"

    LaunchedEffect(profile?.bio) {
        profile?.let { bio = it.bio }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack) { Text("← My Space") }
            Spacer(Modifier.height(10.dp))
            Text("Make it yours", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("A KIN Skin changes the atmosphere of your whole Space, not just one accent color.")
        }

        item {
            Text("Skins", fontWeight = FontWeight.Bold)
            listOf(
                Triple("kin-original", "KIN Original", "Warm personal journal"),
                Triple("midnight", "Midnight", "Dark glass, nightlife mood"),
                Triple("y2k", "Y2K", "Early-web energy, modernized"),
            ).forEach { (id, label, description) ->
                Card(
                    onClick = {
                        if (profile == null) return@Card
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = kinCardShape(id),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(kinMediaShape(id))
                                .background(kinSpaceHeroBrush(id)),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(if (activeSkin == id) "✓ $label" else label, fontWeight = FontWeight.Bold)
                            Text(description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it.take(160); savedMessage = ""; saveFailed = false },
                label = { Text("About this Space") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = kinCardShape(activeSkin),
            )
            Button(
                onClick = {
                    if (profile == null) return@Button
                    scope.launch {
                        when (val result = graph.authRepository.updateProfile(KinProfileUpdate(bio = bio.trim()))) {
                            KinAuthResult.Success -> {
                                savedMessage = "Space bio saved"
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) { Text("Save Space") }
            if (savedMessage.isNotBlank()) {
                Text(
                    savedMessage,
                    modifier = Modifier.padding(top = 8.dp),
                    color = if (saveFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
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
            Text("Your People", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Circles and private notes are relationship context only you can see.")
        }

        if (people.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No connections yet", fontWeight = FontWeight.Bold)
                        Text("Add people first, then decide where they belong in your life.")
                    }
                }
            }
            items(circles, key = { it.id }) { circle -> CircleInfoCard(circle) }
        } else {
            items(people, key = { it.person.id }) { person ->
                RelationshipCard(person = person, allCircles = circles, repository = repository)
            }
        }
    }
}

@Composable
private fun CircleInfoCard(circle: KinCircleEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
    var note by remember(person.person.id, person.person.privateNote) { mutableStateOf(person.person.privateNote) }
    val scope = rememberCoroutineScope()
    val selectedIds = person.circles.map { it.id }.toSet()

    Card(modifier = Modifier.fillMaxWidth()) {
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
                                val next = if (selected) selectedIds - circle.id else selectedIds + circle.id
                                scope.launch { repository.setPersonCircles(person.person.id, next.toList()) }
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
                onClick = { scope.launch { repository.savePrivateNote(person.person.id, note.trim()) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save private note") }
        }
    }
}

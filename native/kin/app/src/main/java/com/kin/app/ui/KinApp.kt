package com.kin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kin.app.KinAppGraph
import com.kin.app.data.KinCircleEntity
import com.kin.app.data.KinPersonWithCircles
import com.kin.app.data.KinProfileEntity
import com.kin.app.data.KinRelationshipRepository
import com.kin.app.session.KinSession
import kotlinx.coroutines.launch

enum class KinRoot(val label: String, val symbol: String) {
    HOME("HOME", "⌂"),
    MOMENT("MOMENT", "◉"),
    CREATE("+", "+"),
    CHAT("CHAT", "✉"),
    ME("ME", "●"),
}

private enum class MeRoute {
    ROOT,
    RELATIONSHIPS,
    CUSTOMIZE,
    GUESTBOOK,
    REMIX,
}

private val kinColors = lightColorScheme(
    primary = Color(0xFF7C5CFC),
    secondary = Color(0xFFFF7FA6),
    tertiary = Color(0xFF4FB7A8),
    surface = Color(0xFFFFFBFF),
    background = Color(0xFFF8F5FF),
)

@Composable
fun KinApp(graph: KinAppGraph, session: KinSession) {
    var selected by rememberSaveable { mutableStateOf(KinRoot.HOME) }

    LaunchedEffect(Unit) {
        graph.relationshipRepository.ensureStarterData()
    }

    MaterialTheme(colorScheme = kinColors) {
        Scaffold(
            topBar = { KinHeader(selected) },
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
                    KinRoot.HOME -> HomeScreen(graph.relationshipRepository)
                    KinRoot.MOMENT -> MomentScreen()
                    KinRoot.CREATE -> CreateScreen()
                    KinRoot.CHAT -> ChatScreen(graph.relationshipRepository)
                    KinRoot.ME -> MeScreen(graph, session)
                }
            }
        }
    }
}

@Composable
private fun KinHeader(selected: KinRoot) {
    Surface(shadowElevation = 1.dp) {
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
            Text(selected.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun HomeScreen(repository: KinRelationshipRepository) {
    val people by repository.observePeople().collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Latest from your people", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Chronological by default — no popularity race.")
            Spacer(Modifier.height(8.dp))
        }
        if (people.isEmpty()) {
            item { SimpleAction("Your people will appear here", "Add connections and give them relationship context with Circles.") }
        } else {
            items(people, key = { it.person.id }) { person -> PersonMomentCard(person) }
        }
    }
}

@Composable
private fun PersonMomentCard(person: KinPersonWithCircles) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(person.person.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(person.person.handle, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                person.circles.take(2).forEach { circle ->
                    AssistChip(onClick = {}, label = { Text(circle.name) })
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Shared a moment", style = MaterialTheme.typography.labelLarge)
            Text("Small updates from people you actually know belong here.")
        }
    }
}

@Composable
private fun MomentScreen() {
    ScreenColumn("Moment", "Share what is happening without turning everything into a performance.") {
        SimpleAction("Feeling", "Happy, tired, excited, chill or your own mood")
        SimpleAction("Listening", "Share what you are listening to")
        SimpleAction("At", "Optional location check-in")
        SimpleAction("With", "Tag people you are spending time with")
        SimpleAction("Thought", "A short text update")
        SimpleAction("Photo", "A quick visual moment")
    }
}

@Composable
private fun CreateScreen() {
    ScreenColumn("Create", "One button for posting. Audience stays under your control.") {
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Create Post") }
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Create Moment") }
        Spacer(Modifier.height(6.dp))
        Text("Audience: Public · Friends · Circle · Only Me", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChatScreen(repository: KinRelationshipRepository) {
    val people by repository.observePeople().collectAsStateWithLifecycle(initialValue = emptyList())
    ScreenColumn("Chat", "Simple private conversations, with relationship context visible only to you.") {
        people.take(5).forEach { person ->
            val context = person.circles.joinToString(" · ") { it.name }.ifBlank { "Friend" }
            SimpleAction("${person.person.displayName} · $context", "Conversation preview")
        }
    }
}

@Composable
private fun MeScreen(graph: KinAppGraph, session: KinSession) {
    val profile by graph.profileRepository.observeProfile().collectAsStateWithLifecycle(initialValue = null)
    var route by rememberSaveable { mutableStateOf(MeRoute.ROOT) }
    val scope = rememberCoroutineScope()

    when (route) {
        MeRoute.RELATIONSHIPS -> {
            RelationshipManagerScreen(
                repository = graph.relationshipRepository,
                onBack = { route = MeRoute.ROOT },
            )
            return
        }

        MeRoute.CUSTOMIZE -> {
            CustomizeSpaceScreen(
                profile = profile,
                graph = graph,
                onBack = { route = MeRoute.ROOT },
            )
            return
        }

        MeRoute.GUESTBOOK -> {
            GuestbookScreen(onBack = { route = MeRoute.ROOT })
            return
        }

        MeRoute.REMIX -> {
            RemixSkinScreen(
                profile = profile,
                graph = graph,
                onBack = { route = MeRoute.ROOT },
            )
            return
        }

        MeRoute.ROOT -> Unit
    }

    ScreenColumn("My Space", "Your profile should feel like your own room on the internet.") {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(profile?.displayName ?: session.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("@${profile?.username ?: session.username}")
                if (!profile?.bio.isNullOrBlank()) Text(profile?.bio.orEmpty())
                Spacer(Modifier.height(12.dp))
                Text("Skin: ${profile?.skinId ?: "kin-original"}")
            }
        }

        Button(onClick = { route = MeRoute.RELATIONSHIPS }, modifier = Modifier.fillMaxWidth()) {
            Text("Manage Circles & Private Notes")
        }

        SimpleAction(
            title = "Customize Space",
            description = "Skin, background, cards, font and profile layout",
            onClick = { route = MeRoute.CUSTOMIZE },
        )
        SimpleAction(
            title = "Guestbook",
            description = "Modern profile messages from your people",
            onClick = { route = MeRoute.GUESTBOOK },
        )
        SimpleAction(
            title = "Remix Skin",
            description = "Reuse a skin structure and make it yours",
            onClick = { route = MeRoute.REMIX },
        )

        OutlinedButton(
            onClick = { scope.launch { graph.sessionStore.signOut() } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Log out") }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CustomizeSpaceScreen(
    profile: KinProfileEntity?,
    graph: KinAppGraph,
    onBack: () -> Unit,
) {
    var selectedSkin by rememberSaveable { mutableStateOf(profile?.skinId ?: "kin-original") }
    var bio by rememberSaveable { mutableStateOf(profile?.bio.orEmpty()) }
    var savedMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(profile?.skinId, profile?.bio) {
        profile?.let {
            selectedSkin = it.skinId
            bio = it.bio
        }
    }

    ScreenColumn("Customize Space", "Change the parts of your profile that already have permanent storage.") {
        OutlinedButton(onClick = onBack) { Text("← My Space") }

        Text("Skin", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("kin-original" to "KIN", "midnight" to "Midnight", "y2k" to "Y2K").forEach { (id, label) ->
                FilterChip(
                    selected = selectedSkin == id,
                    onClick = { selectedSkin = id; savedMessage = "" },
                    label = { Text(label) },
                )
            }
        }

        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it.take(160); savedMessage = "" },
            label = { Text("Short bio") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        Button(
            onClick = {
                val current = profile ?: return@Button
                scope.launch {
                    graph.profileRepository.saveProfile(
                        current.copy(
                            bio = bio.trim(),
                            skinId = selectedSkin,
                        ),
                    )
                    savedMessage = "Saved"
                }
            },
            enabled = profile != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save changes") }

        if (savedMessage.isNotBlank()) {
            Text(savedMessage, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        SimpleAction("Background", "Theme engine slot prepared for the next visual customization slice.")
        SimpleAction("Cards & font", "These will extend the same profile theme model instead of replacing this screen.")
        SimpleAction("Profile layout", "Module ordering will live here when My Space modules are connected.")
    }
}

@Composable
private fun GuestbookScreen(onBack: () -> Unit) {
    ScreenColumn("Guestbook", "A dedicated place for messages left on your profile.") {
        OutlinedButton(onClick = onBack) { Text("← My Space") }
        SimpleAction("No guestbook messages yet", "The screen route is active. Social messages will populate here once connection sync is online.")
        Text(
            "Guestbook privacy will use the same Friends / Circle controls as the rest of KIN.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RemixSkinScreen(
    profile: KinProfileEntity?,
    graph: KinAppGraph,
    onBack: () -> Unit,
) {
    var remixSkin by rememberSaveable { mutableStateOf(profile?.skinId ?: "kin-original") }
    var savedMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(profile?.skinId) {
        profile?.let { remixSkin = it.skinId }
    }

    ScreenColumn("Remix Skin", "Start from an existing skin structure, then make it yours.") {
        OutlinedButton(onClick = onBack) { Text("← My Space") }

        listOf(
            "kin-original" to "KIN Original",
            "midnight" to "Midnight",
            "y2k" to "Y2K",
        ).forEach { (id, label) ->
            SimpleAction(
                title = if (remixSkin == id) "✓ $label" else label,
                description = "Use this as your remix base",
                onClick = { remixSkin = id; savedMessage = "" },
            )
        }

        Button(
            onClick = {
                val current = profile ?: return@Button
                scope.launch {
                    graph.profileRepository.saveProfile(current.copy(skinId = remixSkin))
                    savedMessage = "Remix applied"
                }
            },
            enabled = profile != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply remix") }

        if (savedMessage.isNotBlank()) {
            Text(savedMessage, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
            Text("People & relationship memory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Circle labels and private notes are visible only to you.")
        }
        items(people, key = { it.person.id }) { person ->
            RelationshipCard(person = person, allCircles = circles, repository = repository)
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(person.person.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(person.person.handle)
            Text("Circles", fontWeight = FontWeight.SemiBold)
            allCircles.chunked(3).forEach { row ->
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
                supportingText = { Text("Only you can see this") },
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

@Composable
private fun ScreenColumn(
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
    }
}

@Composable
private fun SimpleAction(
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
) {
    if (onClick == null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            SimpleActionContent(title, description)
        }
    } else {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            SimpleActionContent(title, description)
        }
    }
}

@Composable
private fun SimpleActionContent(title: String, description: String) {
    Column(Modifier.padding(16.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}

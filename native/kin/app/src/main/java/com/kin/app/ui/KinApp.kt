package com.kin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class KinRoot(val label: String, val symbol: String) {
    HOME("HOME", "⌂"),
    MOMENT("MOMENT", "◉"),
    CREATE("+", "+"),
    CHAT("CHAT", "✉"),
    ME("ME", "●"),
}

data class KinPerson(
    val name: String,
    val handle: String,
    val circles: List<String>,
    val note: String,
)

private val kinColors = lightColorScheme(
    primary = Color(0xFF7C5CFC),
    secondary = Color(0xFFFF7FA6),
    tertiary = Color(0xFF4FB7A8),
    surface = Color(0xFFFFFBFF),
    background = Color(0xFFF8F5FF),
)

@Composable
fun KinApp() {
    var selected by remember { mutableStateOf(KinRoot.HOME) }

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
                    KinRoot.HOME -> HomeScreen()
                    KinRoot.MOMENT -> MomentScreen()
                    KinRoot.CREATE -> CreateScreen()
                    KinRoot.CHAT -> ChatScreen()
                    KinRoot.ME -> MeScreen()
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
private fun HomeScreen() {
    val people = listOf(
        KinPerson("Maya", "@maya", listOf("Work", "Close Friends"), "Met through studio project"),
        KinPerson("Raka", "@raka", listOf("Gaming"), "Usually online at night"),
        KinPerson("Nadia", "@nadia", listOf("Family"), "Family circle"),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Latest from your people", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Chronological by default — no popularity race.")
            Spacer(Modifier.height(8.dp))
        }
        items(people) { person -> PersonMomentCard(person) }
    }
}

@Composable
private fun PersonMomentCard(person: KinPerson) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(person.handle, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                person.circles.take(2).forEach { circle ->
                    AssistChip(onClick = {}, label = { Text(circle) })
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
    ScreenColumn(
        title = "Moment",
        subtitle = "Share what is happening without turning everything into a performance.",
    ) {
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
    ScreenColumn(
        title = "Create",
        subtitle = "One button for posting. Audience stays under your control.",
    ) {
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Create Post") }
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Create Moment") }
        Spacer(Modifier.height(6.dp))
        Text("Audience: Public · Friends · Circle · Only Me", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChatScreen() {
    ScreenColumn(
        title = "Chat",
        subtitle = "Simple private conversations, with your relationship context visible only to you.",
    ) {
        SimpleAction("Maya · Work", "Draft review — 18:42")
        SimpleAction("Raka · Gaming", "Main later? — 17:10")
        SimpleAction("Nadia · Family", "Photo — 15:03")
    }
}

@Composable
private fun MeScreen() {
    ScreenColumn(
        title = "My Space",
        subtitle = "Your profile should feel like your own room on the internet.",
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("@yourname", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("KIN Original Skin")
                Spacer(Modifier.height(12.dp))
                Text("Modules: Featured Moment · Album · Music · Guestbook")
            }
        }
        SimpleAction("Customize Space", "Skin, background, cards, font and profile layout")
        SimpleAction("Circles", "Work · Family · School · Gaming · Client · Custom")
        SimpleAction("Private Notes", "Remember how each person fits into your life")
        SimpleAction("Guestbook", "Modern profile messages from your people")
        SimpleAction("Remix Skin", "Reuse a skin structure and make it yours")
    }
}

@Composable
private fun ScreenColumn(
    title: String,
    subtitle: String,
    content: @Composable Column.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
private fun SimpleAction(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

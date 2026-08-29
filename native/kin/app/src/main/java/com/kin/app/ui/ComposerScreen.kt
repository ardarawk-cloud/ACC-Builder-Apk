package com.kin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kin.app.KinAppGraph
import com.kin.app.data.KinPostEntity
import com.kin.app.data.KinProfileEntity
import com.kin.app.media.KinNowPlayingReader
import com.kin.app.session.KinSession
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun ComposerScreen(
    graph: KinAppGraph,
    session: KinSession,
    profile: KinProfileEntity?,
    onPublished: () -> Unit,
) {
    val context = LocalContext.current
    val people by graph.relationshipRepository.observePeople().collectAsStateWithLifecycle(initialValue = emptyList())
    var text by rememberSaveable { mutableStateOf("") }
    var audience by rememberSaveable { mutableStateOf("Friends") }
    var feeling by rememberSaveable { mutableStateOf("") }
    var listening by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var withPersonId by rememberSaveable { mutableStateOf("") }
    var musicStatus by rememberSaveable { mutableStateOf("") }
    var publishing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val withPerson = people.firstOrNull { it.person.id == withPersonId }
    val hasContent = text.isNotBlank() || feeling.isNotBlank() || listening.isNotBlank() || location.isNotBlank() || withPerson != null

    KinScreenColumn(
        title = "Create post",
        subtitle = "One composer. Add context only when you want it.",
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(1000) },
            label = { Text("What's happening?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
        )

        Text("Feeling", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Happy", "Chill", "Excited").forEach { option ->
                FilterChip(
                    selected = feeling == option,
                    onClick = { feeling = if (feeling == option) "" else option },
                    label = { Text(option) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tired", "Busy", "Grateful").forEach { option ->
                FilterChip(
                    selected = feeling == option,
                    onClick = { feeling = if (feeling == option) "" else option },
                    label = { Text(option) },
                )
            }
        }

        Text("Listening", fontWeight = FontWeight.Bold)
        if (listening.isBlank()) {
            Button(
                onClick = {
                    if (!KinNowPlayingReader.hasAccess(context)) {
                        musicStatus = "Enable KIN Music Access once, then return and tap this button again."
                        KinNowPlayingReader.openAccessSettings(context)
                    } else {
                        val nowPlaying = KinNowPlayingReader.read(context)
                        if (nowPlaying == null) {
                            musicStatus = "No active music found. Start a song in your music app, then try again."
                        } else {
                            listening = nowPlaying.displayText
                            musicStatus = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add current music from phone")
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Now playing", fontWeight = FontWeight.Bold)
                    Text(listening)
                    OutlinedButton(onClick = { listening = "" }) { Text("Remove") }
                }
            }
        }
        if (musicStatus.isNotBlank()) {
            Text(musicStatus, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = location,
            onValueChange = { location = it.take(120) },
            label = { Text("Location (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Text("With", fontWeight = FontWeight.Bold)
        if (people.isEmpty()) {
            Text("People can be tagged here once you have real KIN connections.", style = MaterialTheme.typography.bodySmall)
        } else {
            people.take(6).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { person ->
                        FilterChip(
                            selected = withPersonId == person.person.id,
                            onClick = {
                                withPersonId = if (withPersonId == person.person.id) "" else person.person.id
                            },
                            label = { Text(person.person.displayName) },
                        )
                    }
                }
            }
        }

        Text("Who can see it?", fontWeight = FontWeight.Bold)
        KinAudiencePicker(selected = audience, onSelected = { audience = it })

        Button(
            onClick = {
                if (!hasContent || publishing) return@Button
                val authorDisplayName = profile?.displayName?.ifBlank { null }
                    ?: session.displayName.ifBlank { "KIN User" }
                val authorUsername = profile?.username?.ifBlank { null }
                    ?: session.username.ifBlank { "kinuser" }

                val post = KinPostEntity(
                    id = UUID.randomUUID().toString(),
                    authorDisplayName = authorDisplayName,
                    authorUsername = authorUsername,
                    text = text.trim(),
                    audience = audience,
                    feeling = feeling.takeIf { it.isNotBlank() },
                    listening = listening.takeIf { it.isNotBlank() },
                    location = location.trim().takeIf { it.isNotBlank() },
                    withPeople = withPerson?.person?.displayName,
                    createdAt = System.currentTimeMillis(),
                )

                scope.launch {
                    publishing = true
                    graph.postRepository.savePost(post)
                    publishing = false
                    onPublished()
                }
            },
            enabled = hasContent && !publishing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (publishing) "Publishing…" else "Post to Home")
        }
    }
}

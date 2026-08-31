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
import com.kin.app.data.KinPeopleResult
import com.kin.app.data.KinPostEntity
import com.kin.app.data.KinProfileEntity
import com.kin.app.session.KinSession
import com.kin.app.share.KinShareInbox
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun ComposerScreen(
    graph: KinAppGraph,
    session: KinSession,
    profile: KinProfileEntity?,
    onPublished: () -> Unit,
) {
    val people by graph.relationshipRepository.observePeople().collectAsStateWithLifecycle(initialValue = emptyList())
    val circles by graph.relationshipRepository.observeCircles().collectAsStateWithLifecycle(initialValue = emptyList())
    val sharedContent by KinShareInbox.sharedContent.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var audience by rememberSaveable { mutableStateOf("Friends") }
    var feeling by rememberSaveable { mutableStateOf("") }
    var listening by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var withPersonId by rememberSaveable { mutableStateOf("") }
    var selectedCircleIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var publishing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sharedContent) {
        val shared = sharedContent ?: return@LaunchedEffect
        if (listening.isBlank()) listening = shared.text
        KinShareInbox.consume()
    }

    val withPerson = people.firstOrNull { it.person.id == withPersonId }
    val allowedUserIds = people
        .filter { person -> person.circles.any { it.id in selectedCircleIds } }
        .map { it.person.id }
        .distinct()
    val hasContent = text.isNotBlank() || feeling.isNotBlank() || listening.isNotBlank() || location.isNotBlank() || withPerson != null
    val validAudience = audience != "Circle" || selectedCircleIds.isNotEmpty()

    KinScreenColumn(
        title = "Create post",
        subtitle = "One composer. Your post goes straight to the chronological Home feed.",
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(1000); status = "" },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Add music safely", fontWeight = FontWeight.Bold)
                    Text("From Spotify, YouTube Music or another music app: Share → KIN. No notification access is used.")
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Listening to", fontWeight = FontWeight.Bold)
                    Text(listening)
                    OutlinedButton(onClick = { listening = "" }) { Text("Remove") }
                }
            }
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
            Text("Connect with people first to tag someone here.", style = MaterialTheme.typography.bodySmall)
        } else {
            people.take(8).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { person ->
                        FilterChip(
                            selected = withPersonId == person.person.id,
                            onClick = { withPersonId = if (withPersonId == person.person.id) "" else person.person.id },
                            label = { Text(person.person.displayName) },
                        )
                    }
                }
            }
        }

        Text("Who can see it?", fontWeight = FontWeight.Bold)
        KinAudiencePicker(
            selected = audience,
            onSelected = {
                audience = it
                status = ""
            },
        )

        if (audience == "Circle") {
            Text("Choose private Circles", fontWeight = FontWeight.Bold)
            Text("KIN sends only the matching account IDs. Your Circle names stay on this phone.", style = MaterialTheme.typography.bodySmall)
            circles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { circle ->
                        FilterChip(
                            selected = circle.id in selectedCircleIds,
                            onClick = {
                                selectedCircleIds = if (circle.id in selectedCircleIds) {
                                    selectedCircleIds - circle.id
                                } else {
                                    selectedCircleIds + circle.id
                                }
                            },
                            label = { Text(circle.name) },
                        )
                    }
                }
            }
            Text("${allowedUserIds.size} connection(s) match this audience.", style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                if (!hasContent || publishing || !validAudience) return@Button
                val authorDisplayName = profile?.displayName?.ifBlank { null }
                    ?: session.displayName.ifBlank { "KIN User" }
                val authorUsername = profile?.username?.ifBlank { null }
                    ?: session.username.ifBlank { "kinuser" }
                val draft = KinPostEntity(
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
                    status = ""
                    when (val result = graph.postRepository.publishPost(draft, if (audience == "Circle") allowedUserIds else emptyList())) {
                        is KinPeopleResult.Success -> onPublished()
                        is KinPeopleResult.Error -> status = result.message
                    }
                    publishing = false
                }
            },
            enabled = hasContent && validAudience && !publishing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (publishing) "Publishing…" else "Post to Home")
        }
        if (status.isNotBlank()) Text(status)
    }
}

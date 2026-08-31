package com.kin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kin.app.data.KinCircleEntity
import com.kin.app.data.KinFriendRequest
import com.kin.app.data.KinFriendRequests
import com.kin.app.data.KinPeopleResult
import com.kin.app.data.KinPersonWithCircles
import com.kin.app.data.KinRelationshipRepository
import com.kin.app.data.KinRemotePerson
import kotlinx.coroutines.launch

@Composable
fun PeopleV1BScreen(repository: KinRelationshipRepository) {
    val people by repository.observePeople().collectAsStateWithLifecycle(initialValue = emptyList())
    val circles by repository.observeCircles().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<KinRemotePerson>>(emptyList()) }
    var requests by remember { mutableStateOf(KinFriendRequests()) }
    var selectedLocalId by remember { mutableStateOf("") }
    var selectedRemote by remember { mutableStateOf<KinRemotePerson?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun applyRemoteUpdate(updated: KinRemotePerson) {
        searchResults = searchResults.map { person ->
            if (person.id == updated.id) updated else person
        }
        if (selectedRemote?.id == updated.id) selectedRemote = updated
    }

    suspend fun refreshRequestsAndConnections(showError: Boolean = false) {
        repository.syncConnections()
        when (val result = repository.loadFriendRequests()) {
            is KinPeopleResult.Success -> requests = result.value
            is KinPeopleResult.Error -> if (showError) message = result.message
        }
    }

    LaunchedEffect(Unit) {
        refreshRequestsAndConnections()
    }

    val selectedLocal = people.firstOrNull { it.person.id == selectedLocalId }
    if (selectedLocal != null) {
        LocalConnectionDetailScreen(
            person = selectedLocal,
            circles = circles,
            repository = repository,
            onBack = { selectedLocalId = "" },
        )
        return
    }

    selectedRemote?.let { remote ->
        RemotePersonDetailScreen(
            person = remote,
            busy = busy,
            onBack = { selectedRemote = null },
            onAddFriend = {
                scope.launch {
                    busy = true
                    message = ""
                    when (val result = repository.sendFriendRequest(remote.username)) {
                        is KinPeopleResult.Success -> {
                            applyRemoteUpdate(result.value)
                            message = "Friend request sent to @${result.value.username}."
                            refreshRequestsAndConnections()
                        }
                        is KinPeopleResult.Error -> message = result.message
                    }
                    busy = false
                }
            },
        )
        return
    }

    KinScreenColumn(
        title = "People",
        subtitle = "Find real KIN accounts, connect, then organize them privately with Circles.",
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Find someone", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("@username") },
                )
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            message = ""
                            when (val result = repository.searchPeople(query)) {
                                is KinPeopleResult.Success -> {
                                    searchResults = result.value
                                    if (result.value.isEmpty()) message = "No KIN account found."
                                }
                                is KinPeopleResult.Error -> message = result.message
                            }
                            busy = false
                        }
                    },
                    enabled = query.trim().removePrefix("@").isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Search KIN")
                }
                if (busy) CircularProgressIndicator()
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (requests.incoming.isNotEmpty()) {
            Text("Friend requests", fontWeight = FontWeight.Bold)
            requests.incoming.forEach { request ->
                IncomingRequestCard(
                    request = request,
                    busy = busy,
                    onAccept = {
                        scope.launch {
                            busy = true
                            message = ""
                            when (val result = repository.acceptFriendRequest(request.id)) {
                                is KinPeopleResult.Success -> {
                                    applyRemoteUpdate(result.value)
                                    message = "You and @${result.value.username} are now connected."
                                    refreshRequestsAndConnections(showError = true)
                                }
                                is KinPeopleResult.Error -> message = result.message
                            }
                            busy = false
                        }
                    },
                    onDecline = {
                        scope.launch {
                            busy = true
                            message = ""
                            when (val result = repository.declineFriendRequest(request.id)) {
                                is KinPeopleResult.Success -> {
                                    message = "Friend request removed."
                                    refreshRequestsAndConnections(showError = true)
                                }
                                is KinPeopleResult.Error -> message = result.message
                            }
                            busy = false
                        }
                    },
                )
            }
        }

        if (requests.outgoing.isNotEmpty()) {
            Text("Sent requests", fontWeight = FontWeight.Bold)
            requests.outgoing.forEach { request ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(request.person.displayName, fontWeight = FontWeight.Bold)
                        Text(request.person.handle)
                        Text("Waiting for them to accept.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (searchResults.isNotEmpty()) {
            Text("Search results", fontWeight = FontWeight.Bold)
            searchResults.forEach { person ->
                RemotePersonCard(
                    person = person,
                    busy = busy,
                    onView = { selectedRemote = person },
                    onAddFriend = {
                        scope.launch {
                            busy = true
                            message = ""
                            when (val result = repository.sendFriendRequest(person.username)) {
                                is KinPeopleResult.Success -> {
                                    applyRemoteUpdate(result.value)
                                    message = "Friend request sent to @${result.value.username}."
                                    refreshRequestsAndConnections()
                                }
                                is KinPeopleResult.Error -> message = result.message
                            }
                            busy = false
                        }
                    },
                )
            }
        }

        Text("Connections", fontWeight = FontWeight.Bold)
        if (people.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("No connections yet", fontWeight = FontWeight.Bold)
                    Text("Search a KIN username above and send the first friend request.")
                }
            }
        } else {
            people.forEach { person ->
                Card(
                    onClick = { selectedLocalId = person.person.id },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(person.person.displayName, fontWeight = FontWeight.Bold)
                        Text(person.person.handle)
                        Text(person.circles.joinToString(" · ") { it.name }.ifBlank { "No Circle yet" })
                        Text("Tap to manage Circles & private note", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text("Available private Circle labels", fontWeight = FontWeight.Bold)
        circles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { circle ->
                    OutlinedButton(onClick = {}, enabled = false) { Text(circle.name) }
                }
            }
        }
    }
}

@Composable
private fun IncomingRequestCard(
    request: KinFriendRequest,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(request.person.displayName, fontWeight = FontWeight.Bold)
            Text(request.person.handle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept, enabled = !busy) { Text("Accept") }
                OutlinedButton(onClick = onDecline, enabled = !busy) { Text("Decline") }
            }
        }
    }
}

@Composable
private fun RemotePersonCard(
    person: KinRemotePerson,
    busy: Boolean,
    onView: () -> Unit,
    onAddFriend: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(person.displayName, fontWeight = FontWeight.Bold)
            Text(person.handle)
            if (person.bio.isNotBlank()) Text(person.bio)
            Text(relationshipLabel(person.relationship), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onView) { Text("View profile") }
                if (person.relationship == "none") {
                    Button(onClick = onAddFriend, enabled = !busy) { Text("Add friend") }
                }
            }
        }
    }
}

@Composable
private fun RemotePersonDetailScreen(
    person: KinRemotePerson,
    busy: Boolean,
    onBack: () -> Unit,
    onAddFriend: () -> Unit,
) {
    KinScreenColumn(
        title = person.displayName,
        subtitle = person.handle,
    ) {
        OutlinedButton(onClick = onBack) { Text("← People") }
        if (person.bio.isNotBlank()) Text(person.bio)
        Text("Relationship", fontWeight = FontWeight.Bold)
        Text(relationshipLabel(person.relationship))
        when (person.relationship) {
            "none" -> Button(onClick = onAddFriend, enabled = !busy) { Text("Add friend") }
            "outgoing_pending" -> OutlinedButton(onClick = {}, enabled = false) { Text("Request sent") }
            "incoming_pending" -> Text("This person already sent you a friend request. Return to People to accept it.")
            "friends" -> Text("Connected on KIN.")
        }
    }
}

@Composable
private fun LocalConnectionDetailScreen(
    person: KinPersonWithCircles,
    circles: List<KinCircleEntity>,
    repository: KinRelationshipRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedCircleIds by remember(person.person.id) {
        mutableStateOf(person.circles.map { it.id }.toSet())
    }
    var privateNote by remember(person.person.id) { mutableStateOf(person.person.privateNote) }
    var status by remember(person.person.id) { mutableStateOf("") }
    var saving by remember(person.person.id) { mutableStateOf(false) }

    KinScreenColumn(
        title = person.person.displayName,
        subtitle = "Your relationship context stays private to you.",
    ) {
        OutlinedButton(onClick = onBack) { Text("← People") }
        Text(person.person.handle, style = MaterialTheme.typography.titleMedium)

        Text("Choose Circles", fontWeight = FontWeight.Bold)
        Text("You can put one person in more than one Circle.", style = MaterialTheme.typography.bodySmall)
        circles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { circle ->
                    val selected = circle.id in selectedCircleIds
                    if (selected) {
                        Button(
                            onClick = {
                                selectedCircleIds = selectedCircleIds - circle.id
                                status = ""
                            },
                            enabled = !saving,
                        ) {
                            Text("✓ ${circle.name}")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedCircleIds = selectedCircleIds + circle.id
                                status = ""
                            },
                            enabled = !saving,
                        ) {
                            Text(circle.name)
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                scope.launch {
                    saving = true
                    repository.setPersonCircles(person.person.id, selectedCircleIds.toList())
                    status = if (selectedCircleIds.isEmpty()) "Circles cleared." else "Circles saved."
                    saving = false
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Circles")
        }

        Text("Private note", fontWeight = FontWeight.Bold)
        Text("Only you can see this. It is never uploaded to KIN.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = privateNote,
            onValueChange = {
                privateNote = it
                status = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("How do you know this person?") },
            minLines = 2,
            maxLines = 5,
        )
        Button(
            onClick = {
                scope.launch {
                    saving = true
                    repository.savePrivateNote(person.person.id, privateNote.trim())
                    status = if (privateNote.isBlank()) "Private note cleared." else "Private note saved."
                    saving = false
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Private Note")
        }

        if (saving) CircularProgressIndicator()
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun relationshipLabel(value: String): String = when (value) {
    "friends" -> "Connected"
    "outgoing_pending" -> "Request sent"
    "incoming_pending" -> "Request waiting for you"
    else -> "Not connected yet"
}

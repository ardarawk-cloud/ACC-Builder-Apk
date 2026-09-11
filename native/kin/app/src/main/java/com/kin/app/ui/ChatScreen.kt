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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kin.app.data.KinChatRepository
import com.kin.app.data.KinPeopleResult
import com.kin.app.data.KinRelationshipRepository
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    relationshipRepository: KinRelationshipRepository,
    chatRepository: KinChatRepository,
) {
    val people by relationshipRepository.observePeople().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedPersonId by rememberSaveable { mutableStateOf("") }
    val selectedPerson = people.firstOrNull { it.person.id == selectedPersonId }

    if (selectedPerson != null) {
        val messages by chatRepository.observeMessages(selectedPerson.person.id)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val scope = rememberCoroutineScope()
        var message by rememberSaveable(selectedPerson.person.id) { mutableStateOf("") }
        var status by remember(selectedPerson.person.id) { mutableStateOf("") }
        var busy by remember(selectedPerson.person.id) { mutableStateOf(false) }

        suspend fun refresh() {
            busy = true
            when (val result = chatRepository.refreshMessages(selectedPerson.person.id, selectedPerson.person.handle)) {
                is KinPeopleResult.Success -> status = ""
                is KinPeopleResult.Error -> status = result.message
            }
            busy = false
        }

        LaunchedEffect(selectedPerson.person.id) { refresh() }

        KinScreenColumn(
            title = selectedPerson.person.displayName,
            subtitle = selectedPerson.circles.joinToString(" · ") { it.name }.ifBlank { selectedPerson.person.handle },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        selectedPersonId = ""
                        message = ""
                    },
                    enabled = !busy,
                ) { Text("← Chat") }
                OutlinedButton(
                    onClick = { scope.launch { refresh() } },
                    enabled = !busy,
                ) { Text("Refresh") }
            }

            if (busy) CircularProgressIndicator()
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)

            if (messages.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No messages yet", fontWeight = FontWeight.Bold)
                        Text("Start a private conversation with ${selectedPerson.person.displayName}.")
                    }
                }
            } else {
                messages.takeLast(100).forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.mine) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(if (item.mine) "You" else item.senderDisplayName, fontWeight = FontWeight.Bold)
                            Text(item.text)
                            Text(kinFormatPostTime(item.createdAt), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = message,
                onValueChange = {
                    message = it.take(2000)
                    status = ""
                },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 6,
            )
            Button(
                onClick = {
                    val outgoing = message.trim()
                    if (outgoing.isBlank() || busy) return@Button
                    scope.launch {
                        busy = true
                        when (val result = chatRepository.sendMessage(
                            selectedPerson.person.id,
                            selectedPerson.person.handle,
                            outgoing,
                        )) {
                            is KinPeopleResult.Success -> {
                                message = ""
                                status = ""
                            }
                            is KinPeopleResult.Error -> status = result.message
                        }
                        busy = false
                    }
                },
                enabled = message.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send") }
            Text("Messages are stored on KIN and cached on this device for offline viewing.", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    KinScreenColumn("Chat", "Private conversations with your real KIN connections.") {
        if (people.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("No chats yet", fontWeight = FontWeight.Bold)
                    Text("Connect with someone in People first.")
                }
            }
        } else {
            people.forEach { person ->
                Card(
                    onClick = { selectedPersonId = person.person.id },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(person.person.displayName, fontWeight = FontWeight.Bold)
                        Text(person.person.handle)
                        Text(person.circles.joinToString(" · ") { it.name }.ifBlank { "KIN connection" })
                    }
                }
            }
        }
    }
}

package com.kin.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kin.app.data.KinRelationshipRepository

@Composable
fun ChatScreen(repository: KinRelationshipRepository) {
    val people by repository.observePeople().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedPersonId by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var lastSent by rememberSaveable { mutableStateOf("") }
    val selectedPerson = people.firstOrNull { it.person.id == selectedPersonId }

    if (selectedPerson != null) {
        KinScreenColumn(
            title = selectedPerson.person.displayName,
            subtitle = selectedPerson.circles.joinToString(" · ") { it.name }.ifBlank { "KIN connection" },
        ) {
            OutlinedButton(onClick = {
                selectedPersonId = ""
                message = ""
                lastSent = ""
            }) { Text("← Chat") }

            if (lastSent.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("You", fontWeight = FontWeight.Bold)
                        Text(lastSent)
                    }
                }
            }

            OutlinedTextField(
                value = message,
                onValueChange = { message = it.take(1000) },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(
                onClick = {
                    if (message.isNotBlank()) {
                        lastSent = message.trim()
                        message = ""
                    }
                },
                enabled = message.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send") }
            Text("Local preview until remote chat sync is online.", style = MaterialTheme.typography.bodySmall)
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
                    Text("Chats appear after you connect with real people. Demo contacts are no longer shown here.")
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
                        Text(person.circles.joinToString(" · ") { it.name }.ifBlank { "KIN connection" })
                    }
                }
            }
        }
    }
}

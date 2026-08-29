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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.kin.app.data.KinPersonWithCircles
import com.kin.app.data.KinPostEntity
import com.kin.app.data.KinPostRepository
import com.kin.app.data.KinRelationshipRepository

@Composable
fun HomeScreen(repository: KinPostRepository, onCreate: () -> Unit) {
    val posts by repository.observePosts().collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Home", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Posts from you and your people, newest first.")
            Spacer(Modifier.height(4.dp))
        }

        if (posts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("No posts yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Your first post will appear here immediately after you publish it.")
                        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                            Text("Create first post")
                        }
                    }
                }
            }
        } else {
            items(posts, key = { it.id }) { post ->
                PostCard(post)
            }
        }
    }
}

@Composable
private fun PostCard(post: KinPostEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(post.authorDisplayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("@${post.authorUsername} · ${kinFormatPostTime(post.createdAt)}", style = MaterialTheme.typography.labelMedium)

            if (post.text.isNotBlank()) {
                Text(post.text, style = MaterialTheme.typography.bodyLarge)
            }
            post.feeling?.takeIf { it.isNotBlank() }?.let { Text("Feeling · $it") }
            post.listening?.takeIf { it.isNotBlank() }?.let { Text("Listening · $it") }
            post.location?.takeIf { it.isNotBlank() }?.let { Text("At · $it") }
            post.withPeople?.takeIf { it.isNotBlank() }?.let { Text("With · $it") }

            Text("Audience · ${post.audience}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PeopleScreen(repository: KinRelationshipRepository) {
    val people by repository.observePeople().collectAsStateWithLifecycle(initialValue = emptyList())
    val circles by repository.observeCircles().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedPersonId by rememberSaveable { mutableStateOf("") }
    val selectedPerson = people.firstOrNull { it.person.id == selectedPersonId }

    if (selectedPerson != null) {
        PersonDetailScreen(
            person = selectedPerson,
            onBack = { selectedPersonId = "" },
        )
        return
    }

    KinScreenColumn(
        title = "People",
        subtitle = "This is where real connections live. Circles are your private relationship labels.",
    ) {
        if (people.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("No connections yet", fontWeight = FontWeight.Bold)
                    Text("KIN no longer fills this screen with demo contacts. Real accounts will appear here when connection sync is online.")
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
                        Text(person.circles.joinToString(" · ") { it.name }.ifBlank { "No Circle yet" })
                    }
                }
            }
        }

        Text("Your Circle labels", fontWeight = FontWeight.Bold)
        circles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { circle ->
                    AssistChip(onClick = {}, label = { Text(circle.name) })
                }
            }
        }
    }
}

@Composable
private fun PersonDetailScreen(person: KinPersonWithCircles, onBack: () -> Unit) {
    KinScreenColumn(
        title = person.person.displayName,
        subtitle = "Your relationship context stays private to you.",
    ) {
        OutlinedButton(onClick = onBack) { Text("← People") }
        Text(person.person.handle, style = MaterialTheme.typography.titleMedium)
        if (person.circles.isNotEmpty()) {
            Text("Circles", fontWeight = FontWeight.Bold)
            person.circles.forEach { circle -> Text("• ${circle.name}") }
        }
        if (person.person.privateNote.isNotBlank()) {
            Text("Private note", fontWeight = FontWeight.Bold)
            Text(person.person.privateNote)
        }
    }
}

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
import com.kin.app.data.KinPeopleResult
import com.kin.app.data.KinPostEntity
import com.kin.app.data.KinPostRepository
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    repository: KinPostRepository,
    currentUsername: String,
    onCreate: () -> Unit,
) {
    val posts by repository.observePosts().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var busyPostId by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        refreshing = true
        when (val result = repository.refreshFeed()) {
            is KinPeopleResult.Success -> status = ""
            is KinPeopleResult.Error -> status = result.message
        }
        refreshing = false
    }

    LaunchedEffect(Unit) { refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Home", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("You and your people, newest first.")
                }
                OutlinedButton(
                    onClick = { scope.launch { refresh() } },
                    enabled = !refreshing,
                ) { Text("Refresh") }
            }
            if (refreshing) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
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
                        Text("Create a post or refresh after one of your KIN connections posts.")
                        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                            Text("Create first post")
                        }
                    }
                }
            }
        } else {
            items(posts, key = { it.id }) { post ->
                val mine = post.authorUsername.equals(currentUsername, ignoreCase = true)
                PostCard(
                    post = post,
                    mine = mine,
                    editing = editingId == post.id,
                    editText = if (editingId == post.id) editText else post.text,
                    busy = busyPostId == post.id,
                    onEditText = { editText = it.take(1000) },
                    onStartEdit = {
                        editingId = post.id
                        editText = post.text
                        status = ""
                    },
                    onCancelEdit = {
                        editingId = null
                        editText = ""
                    },
                    onSaveEdit = {
                        scope.launch {
                            busyPostId = post.id
                            when (val result = repository.editPost(post.id, editText)) {
                                is KinPeopleResult.Success -> {
                                    editingId = null
                                    editText = ""
                                    status = ""
                                }
                                is KinPeopleResult.Error -> status = result.message
                            }
                            busyPostId = null
                        }
                    },
                    onDelete = {
                        scope.launch {
                            busyPostId = post.id
                            when (val result = repository.deletePost(post.id)) {
                                is KinPeopleResult.Success -> {
                                    if (editingId == post.id) editingId = null
                                    status = ""
                                }
                                is KinPeopleResult.Error -> status = result.message
                            }
                            busyPostId = null
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PostCard(
    post: KinPostEntity,
    mine: Boolean,
    editing: Boolean,
    editText: String,
    busy: Boolean,
    onEditText: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onDelete: () -> Unit,
) {
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

            if (editing) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = onEditText,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Edit post") },
                    minLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSaveEdit, enabled = !busy && editText.isNotBlank()) { Text("Save") }
                    OutlinedButton(onClick = onCancelEdit, enabled = !busy) { Text("Cancel") }
                }
            } else if (post.text.isNotBlank()) {
                Text(post.text, style = MaterialTheme.typography.bodyLarge)
            }

            post.feeling?.takeIf { it.isNotBlank() }?.let { Text("Feeling · $it") }
            post.listening?.takeIf { it.isNotBlank() }?.let { Text("Listening · $it") }
            post.location?.takeIf { it.isNotBlank() }?.let { Text("At · $it") }
            post.withPeople?.takeIf { it.isNotBlank() }?.let { Text("With · $it") }
            Text("Audience · ${post.audience}", style = MaterialTheme.typography.labelSmall)

            if (mine && !editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onStartEdit, enabled = !busy) { Text("Edit") }
                    OutlinedButton(onClick = onDelete, enabled = !busy) { Text("Delete") }
                }
            }
            if (busy) CircularProgressIndicator()
        }
    }
}

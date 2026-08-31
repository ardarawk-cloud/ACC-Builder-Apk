package com.kin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kin.app.data.KinPeopleResult
import com.kin.app.data.KinPostEntity
import com.kin.app.data.KinPostRepository
import com.kin.app.data.kinPostMediaFromJson
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    repository: KinPostRepository,
    currentUsername: String,
    skinId: String,
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .kinY2kBorder(skinId),
                shape = kinCardShape(skinId),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Share your day…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Choose who gets to see it.", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { scope.launch { refresh() } }, enabled = !refreshing) {
                            Text(if (refreshing) "…" else "↻")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCreate, modifier = Modifier.weight(1f)) { Text("▣ Photo") }
                        OutlinedButton(onClick = onCreate, modifier = Modifier.weight(1f)) { Text("▶ Video") }
                        OutlinedButton(onClick = onCreate, modifier = Modifier.weight(1f)) { Text("☺ Moment") }
                    }
                }
            }
            if (status.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = kinCardShape(skinId),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                ) {
                    Text(status, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (posts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = kinCardShape(skinId),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Your KIN starts here", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Share a photo, video or small moment with people who actually matter to you.")
                        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Share first moment") }
                    }
                }
            }
        } else {
            items(posts, key = { it.id }) { post ->
                val mine = post.authorUsername.equals(currentUsername, ignoreCase = true)
                KinFeedPostCard(
                    post = post,
                    skinId = skinId,
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
fun KinFeedPostCard(
    post: KinPostEntity,
    skinId: String,
    mine: Boolean,
    editing: Boolean = false,
    editText: String = post.text,
    busy: Boolean = false,
    onEditText: (String) -> Unit = {},
    onStartEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onSaveEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    compact: Boolean = false,
) {
    val media = remember(post.mediaJson) { kinPostMediaFromJson(post.mediaJson) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .kinY2kBorder(skinId),
        shape = kinCardShape(skinId),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.authorDisplayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("@${post.authorUsername} · ${kinFormatPostTime(post.createdAt)}", style = MaterialTheme.typography.labelMedium)
                }
                Surface(
                    shape = kinCardShape(skinId),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        audienceLabel(post.audience),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (media.isNotEmpty()) {
                Box(modifier = Modifier.padding(horizontal = if (skinId == "y2k") 8.dp else 10.dp)) {
                    KinPostMediaStrip(media = media, skinId = skinId, compact = compact)
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = if (media.isEmpty()) 0.dp else 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (editing) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = onEditText,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Edit caption") },
                        minLines = 2,
                        shape = kinCardShape(skinId),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSaveEdit, enabled = !busy && editText.isNotBlank()) { Text("Save") }
                        OutlinedButton(onClick = onCancelEdit, enabled = !busy) { Text("Cancel") }
                    }
                } else if (post.text.isNotBlank()) {
                    Text(post.text, style = MaterialTheme.typography.bodyLarge)
                }

                val contextParts = buildList {
                    post.feeling?.takeIf { it.isNotBlank() }?.let { add("☺ $it") }
                    post.listening?.takeIf { it.isNotBlank() }?.let { add("♫ $it") }
                    post.location?.takeIf { it.isNotBlank() }?.let { add("⌖ $it") }
                    post.withPeople?.takeIf { it.isNotBlank() }?.let { add("With $it") }
                }
                if (contextParts.isNotEmpty()) {
                    Text(contextParts.joinToString("   ·   "), style = MaterialTheme.typography.bodySmall)
                }

                if (mine && !editing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (post.text.isNotBlank()) {
                            TextButton(onClick = onStartEdit, enabled = !busy) { Text("Edit") }
                        }
                        TextButton(onClick = onDelete, enabled = !busy) { Text("Delete") }
                    }
                }
                if (busy) CircularProgressIndicator()
            }
        }
    }
}

private fun audienceLabel(value: String): String = when (value) {
    "Public" -> "◉ Public"
    "Only Me" -> "▣ Only Me"
    "Circle" -> "★ Circle"
    else -> "♡ Friends"
}

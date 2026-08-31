package com.kin.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kin.app.KinAppGraph
import com.kin.app.data.KinPeopleResult
import com.kin.app.data.KinPostEntity
import com.kin.app.data.KinPostMedia
import com.kin.app.data.KinProfileEntity
import com.kin.app.data.kinPostMediaToJson
import com.kin.app.session.KinSession
import com.kin.app.share.KinShareInbox
import java.util.UUID
import kotlinx.coroutines.launch

private data class SelectedKinMedia(
    val uri: Uri,
    val type: String,
)

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
    val context = LocalContext.current
    val skinId = profile?.skinId ?: "kin-original"
    val scope = rememberCoroutineScope()

    var text by rememberSaveable { mutableStateOf("") }
    var audience by rememberSaveable { mutableStateOf("Friends") }
    var feeling by rememberSaveable { mutableStateOf("") }
    var listening by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var withPersonId by rememberSaveable { mutableStateOf("") }
    var selectedCircleIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedMedia by remember { mutableStateOf<List<SelectedKinMedia>>(emptyList()) }
    var publishing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedMedia = uris.take(6).map { SelectedKinMedia(it, "image") }
        status = if (uris.size > 6) "KIN keeps up to 6 photos in one post." else ""
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedMedia = listOf(SelectedKinMedia(uri, "video"))
            status = ""
        }
    }

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
    val hasContent = text.isNotBlank() || feeling.isNotBlank() || listening.isNotBlank() || location.isNotBlank() || withPerson != null || selectedMedia.isNotEmpty()
    val validAudience = audience != "Circle" || selectedCircleIds.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("New post", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Share real life with the right people.", style = MaterialTheme.typography.bodyMedium)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { photoPicker.launch("image/*") }, enabled = !publishing) { Text("▣  Photo") }
                OutlinedButton(onClick = { videoPicker.launch("video/*") }, enabled = !publishing) { Text("▶  Video") }
                if (selectedMedia.isNotEmpty()) {
                    OutlinedButton(onClick = { selectedMedia = emptyList() }, enabled = !publishing) { Text("Clear") }
                }
            }
        }

        if (selectedMedia.isNotEmpty()) {
            item {
                if (selectedMedia.first().type == "video") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = kinCardShape(skinId),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("▶ Video selected", fontWeight = FontWeight.Bold)
                            Text("One video per post in this alpha. KIN uploads it privately when you publish.")
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedMedia, key = { it.uri.toString() }) { item ->
                            AsyncImage(
                                model = item.uri,
                                contentDescription = "Selected photo",
                                modifier = Modifier
                                    .width(210.dp)
                                    .height(210.dp)
                                    .clip(kinMediaShape(skinId)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(1000); status = "" },
                label = { Text("Write a caption…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = kinCardShape(skinId),
            )
        }

        item {
            Text("Moment", fontWeight = FontWeight.Bold)
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
                listOf("Busy", "Grateful", "Tired").forEach { option ->
                    FilterChip(
                        selected = feeling == option,
                        onClick = { feeling = if (feeling == option) "" else option },
                        label = { Text(option) },
                    )
                }
            }
        }

        item {
            Text("Listening", fontWeight = FontWeight.Bold)
            if (listening.isBlank()) {
                Text(
                    "From Spotify / YouTube Music: Share → KIN. Music stays privacy-safe without notification access.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = kinCardShape(skinId),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("♫ $listening", fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { listening = "" }) { Text("Remove") }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = location,
                onValueChange = { location = it.take(120) },
                label = { Text("⌖ Location (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = kinCardShape(skinId),
            )
        }

        item {
            Text("With", fontWeight = FontWeight.Bold)
            if (people.isEmpty()) {
                Text("Connect with someone to tag them here.", style = MaterialTheme.typography.bodySmall)
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
        }

        item {
            Text("Who can see this?", fontWeight = FontWeight.Bold)
            Text("Pick the people this moment belongs to.", style = MaterialTheme.typography.bodySmall)
            KinAudiencePicker(
                selected = audience,
                onSelected = {
                    audience = it
                    status = ""
                },
            )
        }

        if (audience == "Circle") {
            item {
                Text("Choose Circles", fontWeight = FontWeight.Bold)
                circles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { circle ->
                            FilterChip(
                                selected = circle.id in selectedCircleIds,
                                onClick = {
                                    selectedCircleIds = if (circle.id in selectedCircleIds) selectedCircleIds - circle.id else selectedCircleIds + circle.id
                                },
                                label = { Text(circle.name) },
                            )
                        }
                    }
                }
                Text("${allowedUserIds.size} connection(s) will receive this post.", style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Button(
                onClick = {
                    if (!hasContent || publishing || !validAudience) return@Button
                    scope.launch {
                        publishing = true
                        status = ""
                        val uploaded = mutableListOf<KinPostMedia>()
                        var uploadError: String? = null

                        for ((index, item) in selectedMedia.withIndex()) {
                            val resolver = context.contentResolver
                            val contentType = resolver.getType(item.uri)
                                ?: if (item.type == "video") "video/mp4" else "image/jpeg"
                            val limit = if (item.type == "video") 40L * 1024 * 1024 else 12L * 1024 * 1024
                            val declaredSize = runCatching {
                                resolver.openAssetFileDescriptor(item.uri, "r")?.use { it.length }
                            }.getOrNull() ?: -1L
                            if (declaredSize > limit) {
                                uploadError = if (item.type == "video") "Video is over 40 MB. Use a shorter clip for now." else "Photo ${index + 1} is over 12 MB."
                                break
                            }
                            val bytes = runCatching {
                                resolver.openInputStream(item.uri)?.use { it.readBytes() }
                            }.getOrNull()
                            if (bytes == null || bytes.isEmpty()) {
                                uploadError = "Could not read selected media."
                                break
                            }
                            if (bytes.size.toLong() > limit) {
                                uploadError = "Selected media is too large for this KIN alpha."
                                break
                            }
                            when (val upload = graph.postRepository.uploadMedia(bytes, contentType, "kin-${item.type}-${index + 1}")) {
                                is KinPeopleResult.Success -> uploaded += upload.value
                                is KinPeopleResult.Error -> {
                                    uploadError = upload.message
                                    break
                                }
                            }
                        }

                        if (uploadError != null) {
                            status = uploadError
                            publishing = false
                            return@launch
                        }

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
                            mediaJson = kinPostMediaToJson(uploaded),
                            createdAt = System.currentTimeMillis(),
                        )
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
                Text(if (publishing) "Publishing…" else "Share to KIN")
            }
            if (status.isNotBlank()) {
                Text(status, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

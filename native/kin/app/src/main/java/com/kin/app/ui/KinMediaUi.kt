package com.kin.app.ui

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.kin.app.data.KinPostMedia

@Composable
fun KinPostMediaStrip(
    media: List<KinPostMedia>,
    skinId: String,
    compact: Boolean = false,
) {
    if (media.isEmpty()) return
    if (media.size == 1) {
        KinMediaItem(
            item = media.first(),
            skinId = skinId,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 190.dp else 300.dp),
        )
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(media, key = { it.id }) { item ->
            KinMediaItem(
                item = item,
                skinId = skinId,
                modifier = Modifier
                    .width(if (compact) 180.dp else 292.dp)
                    .height(if (compact) 180.dp else 292.dp),
            )
        }
    }
}

@Composable
private fun KinMediaItem(
    item: KinPostMedia,
    skinId: String,
    modifier: Modifier,
) {
    val shape = kinMediaShape(skinId)
    if (item.type == "video") {
        Box(
            modifier = modifier.clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { context ->
                    VideoView(context).apply {
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        tag = item.url
                        setVideoURI(Uri.parse(item.url))
                        setOnPreparedListener { player ->
                            player.isLooping = false
                            seekTo(1)
                        }
                    }
                },
                update = { view ->
                    val current = view.tag as? String
                    if (current != item.url) {
                        view.tag = item.url
                        view.setVideoURI(Uri.parse(item.url))
                    }
                },
            )
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = kinCardShape(skinId),
            ) {
                Text("▶ Video", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    } else {
        AsyncImage(
            model = item.url,
            contentDescription = "KIN post photo",
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop,
        )
    }
}

package com.kin.app.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService

data class KinNowPlaying(
    val title: String,
    val artist: String,
    val packageName: String,
) {
    val displayText: String
        get() = if (artist.isBlank()) title else "$title — $artist"
}

class KinNotificationListenerService : NotificationListenerService()

object KinNowPlayingReader {
    fun hasAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()

        return enabled
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it.packageName == context.packageName }
    }

    fun openAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    fun read(context: Context): KinNowPlaying? {
        if (!hasAccess(context)) return null

        return try {
            val manager = context.getSystemService(MediaSessionManager::class.java)
            val listener = ComponentName(context, KinNotificationListenerService::class.java)
            val sessions = manager.getActiveSessions(listener)

            sessions
                .sortedByDescending(::playbackPriority)
                .mapNotNull(::toNowPlaying)
                .firstOrNull()
        } catch (_: SecurityException) {
            null
        }
    }

    private fun playbackPriority(controller: MediaController): Int = when (controller.playbackState?.state) {
        PlaybackState.STATE_PLAYING -> 3
        PlaybackState.STATE_BUFFERING -> 2
        PlaybackState.STATE_PAUSED -> 1
        else -> 0
    }

    private fun toNowPlaying(controller: MediaController): KinNowPlaying? {
        val metadata = controller.metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().trim()
        if (title.isBlank()) return null

        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            .orEmpty()
            .ifBlank { metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty() }
            .trim()

        return KinNowPlaying(
            title = title,
            artist = artist,
            packageName = controller.packageName,
        )
    }
}

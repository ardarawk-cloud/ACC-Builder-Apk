package com.kin.app.share

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class KinSharedContent(
    val text: String,
)

object KinShareInbox {
    private val _sharedContent = MutableStateFlow<KinSharedContent?>(null)
    val sharedContent: StateFlow<KinSharedContent?> = _sharedContent.asStateFlow()

    fun receive(text: String?) {
        val clean = text.orEmpty().trim()
        if (clean.isNotBlank()) {
            _sharedContent.value = KinSharedContent(clean)
        }
    }

    fun consume() {
        _sharedContent.value = null
    }
}

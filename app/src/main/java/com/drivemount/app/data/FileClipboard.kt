package com.drivemount.app.data

import com.drivemount.app.engine.RemoteEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** কপি/কাটা জিনিসগুলো; এক অ্যাকাউন্ট থেকে অন্য অ্যাকাউন্টে পেস্ট করা যায় */
data class ClipboardContent(
    val source: AccountEntity,
    val items: List<RemoteEntry>,
    val move: Boolean,
)

class FileClipboard {
    private val _content = MutableStateFlow<ClipboardContent?>(null)
    val content: StateFlow<ClipboardContent?> = _content

    fun set(content: ClipboardContent) {
        _content.value = content
    }

    fun clear() {
        _content.value = null
    }
}

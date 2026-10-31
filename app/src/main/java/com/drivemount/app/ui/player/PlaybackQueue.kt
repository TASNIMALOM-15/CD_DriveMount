package com.drivemount.app.ui.player

import com.drivemount.app.engine.RemoteEntry

/**
 * Browser থেকে Player-এ প্লেলিস্ট পৌঁছে দেওয়ার জায়গা।
 * items = ফোল্ডারের চালানো-যায় এমন ফাইল (ব্যবহারকারীর সাজানো ক্রমে),
 * siblings = ফোল্ডারের সব ফাইল (পাশের সাবটাইটেল ফাইল খুঁজতে)।
 */
object PlaybackQueue {

    class Queue(
        val accountId: Long,
        val items: List<RemoteEntry>,
        val siblings: List<RemoteEntry>,
    )

    @Volatile
    private var current: Queue? = null

    fun set(queue: Queue) {
        current = queue
    }

    fun peek(accountId: Long): Queue? = current?.takeIf { it.accountId == accountId }
}

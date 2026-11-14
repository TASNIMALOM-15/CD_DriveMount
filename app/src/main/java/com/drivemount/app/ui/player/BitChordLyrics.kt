package com.drivemount.app.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** One lyric line. [timeMs] is 0 for unsynced (plain) lyrics. A blank [text] is an instrumental gap. */
internal data class BcLyricLine(val timeMs: Long, val text: String) {
    val isGap: Boolean get() = text.isBlank()
}

internal sealed interface BcLyrics {
    data object Loading : BcLyrics
    data object None : BcLyrics
    data class Found(val lines: List<BcLyricLine>, val synced: Boolean, val source: String) : BcLyrics
}

/**
 * Synced lyrics from LRCLIB (https://lrclib.net) — free, no API key.
 * Matches by title + artist (and the track duration when known).
 */
internal object BcLyricsRepository {
    private val client = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private val cache = object : LinkedHashMap<String, BcLyrics>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BcLyrics>?) = size > 40
    }

    suspend fun load(title: String, artist: String, durationSec: Int): BcLyrics {
        val key = "${title.lowercase()}|${artist.lowercase()}"
        synchronized(cache) { cache[key] }?.let { return it }
        val result = withContext(Dispatchers.IO) {
            runCatching { fetch(title, artist, durationSec) }.getOrNull()
        }
        // network failure → don't cache, so the next open retries
        if (result == null) return BcLyrics.None
        synchronized(cache) { cache[key] = result }
        return result
    }

    private fun fetch(rawTitle: String, rawArtist: String, durationSec: Int): BcLyrics {
        val title = cleanTitle(rawTitle)
        val artist = rawArtist.trim()
        val queries = buildList {
            if (artist.isNotBlank()) add(mapOf("track_name" to title, "artist_name" to artist))
            add(mapOf("q" to listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")))
            add(mapOf("q" to title))
        }.distinct()
        for (query in queries) {
            val best = search(query, durationSec) ?: continue
            return best
        }
        return BcLyrics.None
    }

    private fun search(params: Map<String, String>, durationSec: Int): BcLyrics? {
        val url = HttpUrl.Builder().scheme("https").host("lrclib.net").addPathSegments("api/search")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val request = Request.Builder().url(url).header("User-Agent", "DriveMount/1.0").build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string() ?: return null
        }
        val array = JSONArray(body)
        var bestScore = Int.MAX_VALUE
        var bestSynced: String? = null
        var bestPlain: String? = null
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val synced = item.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            val plain = item.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
            if (synced == null && plain == null) continue
            val diff = if (durationSec > 0) abs(item.optDouble("duration", 0.0) - durationSec).toInt() else 0
            val score = diff + (if (synced == null) 8 else 0)
            if (score < bestScore) {
                bestScore = score
                bestSynced = synced
                bestPlain = plain
            }
        }
        bestSynced?.let { text ->
            val lines = parseLrc(text)
            if (lines.isNotEmpty()) return BcLyrics.Found(lines, synced = true, source = "LRCLIB")
        }
        bestPlain?.let { text ->
            val lines = text.lines().map { BcLyricLine(0L, it.trim()) }
            if (lines.any { it.text.isNotBlank() }) return BcLyrics.Found(lines, synced = false, source = "LRCLIB")
        }
        return null
    }

    private val tag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    private fun parseLrc(text: String): List<BcLyricLine> {
        val out = ArrayList<BcLyricLine>()
        for (raw in text.lines()) {
            val matches = tag.findAll(raw).toList()
            if (matches.isEmpty() || matches.first().range.first != 0) continue
            val content = raw.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                out += BcLyricLine((min * 60 + sec) * 1000 + ms, content)
            }
        }
        out.sortBy { it.timeMs }
        return out
    }

    /** "Banjaare | Barsaat x Spider-Man (Brand New Day).mp3" → "Banjaare Barsaat x Spider-Man" */
    private fun cleanTitle(raw: String): String =
        raw.substringBeforeLast('.', raw)
            .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
            .replace('|', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { raw.trim() }
}

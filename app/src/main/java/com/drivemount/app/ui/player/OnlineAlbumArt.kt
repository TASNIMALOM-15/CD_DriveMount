package com.drivemount.app.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Online album-art lookup (same idea as the lyrics lookup): iTunes Search first, Deezer as fallback —
 * both are free and need no API key. Used only when the file has no embedded art or a tiny one.
 * Network failures are not cached (retry next time); "no match" is remembered for the session.
 */
object OnlineAlbumArt {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val cache = LruCache<String, Bitmap>(6)
    private val noMatch = ConcurrentHashMap.newKeySet<String>()

    suspend fun load(rawTitle: String, rawArtist: String, rawAlbum: String): Bitmap? {
        val title = clean(rawTitle)
        if (title.isBlank()) return null
        val artist = rawArtist.trim()
        val album = rawAlbum.trim()
        val key = "${norm(title)}|${norm(artist)}"
        cache.get(key)?.let { return it }
        if (key in noMatch) return null
        return withContext(Dispatchers.IO) {
            var failed = false
            var url: String? = null
            runCatching { url = itunes(title, artist, album) }.onFailure { failed = true }
            if (url == null) runCatching { url = deezer(title, artist, album) }.onFailure { failed = true }
            val u = url
            if (u == null) { if (!failed) noMatch.add(key); return@withContext null }
            val bmp = runCatching { download(u) }.getOrNull()
            if (bmp != null) cache.put(key, bmp) else if (!failed) noMatch.add(key)
            bmp
        }
    }

    private fun clean(raw: String): String =
        raw.substringBeforeLast('.', raw)
            .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
            .replace(Regex("""^\s*\d{1,3}\s*[-_. ]\s*"""), "")
            .replace('_', ' ')
            .replace(Regex("""\s+"""), " ").trim()

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** title must match; artist must match when we know it; album match only breaks ties */
    private fun score(title: String, artist: String, album: String, rt: String, ra: String, rl: String): Int {
        val t = norm(title); val a = norm(artist); val l = norm(album)
        val xt = norm(rt); val xa = norm(ra); val xl = norm(rl)
        val ts = when { xt == t -> 3; xt.contains(t) || t.contains(xt) && xt.length > 3 -> 2; else -> 0 }
        if (ts == 0) return 0
        val asc = if (a.isEmpty()) 1 else when { xa == a -> 3; xa.contains(a) || a.contains(xa) && xa.length > 2 -> 2; else -> 0 }
        if (asc == 0) return 0
        return ts * 10 + asc * 3 + (if (l.isNotEmpty() && (xl == l || xl.contains(l))) 2 else 0)
    }

    private fun get(url: HttpUrl): String? =
        client.newCall(Request.Builder().url(url).header("User-Agent", "DriveMount/1.0").build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            r.body?.string()
        }

    private fun itunes(title: String, artist: String, album: String): String? {
        val term = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        val url = HttpUrl.Builder().scheme("https").host("itunes.apple.com").addPathSegment("search")
            .addQueryParameter("term", term).addQueryParameter("media", "music")
            .addQueryParameter("entity", "song").addQueryParameter("limit", "10").build()
        val arr = JSONObject(get(url) ?: return null).optJSONArray("results") ?: return null
        var best = 0; var bestUrl: String? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val art = o.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: continue
            val s = score(title, artist, album, o.optString("trackName"), o.optString("artistName"), o.optString("collectionName"))
            if (s > best) { best = s; bestUrl = art }
        }
        return bestUrl?.replace(Regex("""/\d+x\d+(bb)?\.(jpg|png)$"""), "/1200x1200bb.jpg")
    }

    private fun deezer(title: String, artist: String, album: String): String? {
        val q = if (artist.isNotBlank()) "artist:\"$artist\" track:\"$title\"" else title
        val url = HttpUrl.Builder().scheme("https").host("api.deezer.com").addPathSegment("search")
            .addQueryParameter("q", q).addQueryParameter("limit", "10").build()
        val arr = JSONObject(get(url) ?: return null).optJSONArray("data") ?: return null
        var best = 0; var bestUrl: String? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val alb = o.optJSONObject("album") ?: continue
            val cover = alb.optString("cover_xl").takeIf { it.isNotBlank() } ?: alb.optString("cover_big").takeIf { it.isNotBlank() } ?: continue
            val s = score(title, artist, album, o.optString("title"), o.optJSONObject("artist")?.optString("name").orEmpty(), alb.optString("title"))
            if (s > best) { best = s; bestUrl = cover }
        }
        return bestUrl
    }

    private fun download(url: String): Bitmap? {
        val bytes = client.newCall(Request.Builder().url(url).header("User-Agent", "DriveMount/1.0").build()).execute().use { r ->
            if (!r.isSuccessful) return null
            r.body?.bytes()
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

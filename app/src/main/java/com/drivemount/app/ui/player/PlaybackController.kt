package com.drivemount.app.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.drivemount.app.DriveMountApp
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.engine.RemoteEntry
import com.drivemount.app.teraplay.TeraBoxClient
import com.drivemount.app.teraplay.TpAccountStore
import com.drivemount.app.engine.StreamServer
import com.drivemount.app.engine.StreamWarmer
import com.drivemount.app.engine.fsRootFor
import com.drivemount.app.engine.guessMime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * অ্যাপ-জুড়ে একটাই প্লেয়ার (আগে এটি PlayerViewModel-এ ছিল, তাই পর্দা বদলালে বন্ধ হয়ে যেত)।
 * এখন ভিডিও/গান চলা অবস্থায় যেকোনো ট্যাব বা পর্দায় যাওয়া যায়; প্লেয়ারের চেহারা (পূর্ণ / মিনি) [mode] ঠিক করে।
 *
 * ফাইল আগে পুরো নামানো হয় না: লোকাল streaming সার্ভার প্রয়োজনীয় অংশ ক্লাউড থেকে এনে
 * (এনক্রিপ্টেড ভল্ট হলে ডিক্রিপ্ট করে) প্লেয়ারকে দেয়, তাই দ্রুত চালু হয় ও এগিয়ে-পেছানো (seek) যায়।
 * প্রতিটি ফাইলের লিংক চালানোর মুহূর্তে (লোডার থ্রেডে) তৈরি হয়।
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackController(private val app: DriveMountApp) {

    /** HIDDEN = কিছু চলছে না, EXPANDED = YouTube-স্টাইলের বড় প্লেয়ার, MINI = ভাসমান ছোট প্লেয়ার */
    enum class Mode { HIDDEN, EXPANDED, MINI }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val positions = app.getSharedPreferences("player_positions", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(Mode.HIDDEN)
    val mode: StateFlow<Mode> = _mode

    /** ভিডিওর পূর্ণ-পর্দা (আগের PlayerScreen) চালু আছে কি */
    private val _fullscreen = MutableStateFlow(false)
    val fullscreen: StateFlow<Boolean> = _fullscreen

    /** বর্তমান ফাইলটি গান কি (তাহলে Now-Playing পর্দা ও ব্যাকগ্রাউন্ড প্লেব্যাক) */
    private val _isAudio = MutableStateFlow(false)
    val isAudio: StateFlow<Boolean> = _isAudio

    /** নিচের ট্যাব-বারের উচ্চতা (px); মিনি প্লেয়ার যেন তার ওপরে থাকে। বার না থাকলে 0 */
    val bottomBarPx = MutableStateFlow(0)

    /** মিনি প্লেয়ার শেষ যে কোনায় ছিল: 0 = ওপর-বাম, 1 = ওপর-ডান, 2 = নিচ-বাম, 3 = নিচ-ডান */
    @Volatile
    var miniCorner: Int = 3

    private val _account = MutableStateFlow<AccountEntity?>(null)
    val account: StateFlow<AccountEntity?> = _account

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** প্লেলিস্ট (ক্রম বদলালেও এটি স্থির; shuffle ExoPlayer নিজে সামলায়) */
    private val _queue = MutableStateFlow<List<RemoteEntry>>(emptyList())
    val queue: StateFlow<List<RemoteEntry>> = _queue
    val items: List<RemoteEntry> get() = _queue.value

    private var accountId: Long = -1L

    /** ExoPlayer-এর "dm://f/N" লিংক যে দূরবর্তী পথে মিলবে তার তালিকা (ভিডিও ও সাবটাইটেল একসাথে) */
    private val sources = CopyOnWriteArrayList<String>()
    private val itemSource = ArrayList<Int>()
    private val resolved = ConcurrentHashMap<Int, String>()
    private val resolvedHls = ConcurrentHashMap<Int, String>()

    /** Terabox ভিডিও: সার্ভারের নিজস্ব HLS স্ট্রিমে চলছে এমন ফাইলের পথ (ব্যর্থ হলে মূল ফাইলে ফিরে যায়) */
    private val hlsPaths = HashSet<String>()
    private var useHls = false
    private var siblingList: List<RemoteEntry> = emptyList()

    @Volatile
    private var fsSpec: String = ""

    private var lastIndex = -1
    private var loadJob: Job? = null
    private var stallJob: Job? = null

    /** নতুন তালিকা বসানোর মাঝে অবস্থান সংরক্ষণ বন্ধ (নইলে ভুল ফাইলের সংরক্ষিত অবস্থান মুছে যেত) */
    private var muteSave = false

    private val tpLoaders = CopyOnWriteArrayList<() -> String>()
    private val tpResolved = ConcurrentHashMap<Int, String>()

    private val resolver = ResolvingDataSource.Resolver { spec ->
        if (spec.uri.scheme == "tp") {
            val i = spec.uri.lastPathSegment?.toIntOrNull() ?: throw IOException("Bad link")
            val uri = try {
                tpResolved.getOrPut(i) {
                    val text = tpLoaders[i]()
                    val f = java.io.File(
                        java.io.File(app.cacheDir, "teraplay").also { it.mkdirs() },
                        "t${i}_${System.currentTimeMillis()}.m3u8",
                    )
                    f.writeText(text)
                    Uri.fromFile(f).toString()
                }
            } catch (t: Throwable) {
                throw IOException(t.message ?: t.toString(), t)
            }
            return@Resolver spec.withUri(Uri.parse(uri))
        }
        if (spec.uri.scheme != "dm") return@Resolver spec
        val index = spec.uri.lastPathSegment?.toIntOrNull() ?: throw IOException("Bad link")
        // অফলাইনে রাখা ফাইল থাকলে সরাসরি ফোনের কপি থেকে (নেট ছাড়াই, তাৎক্ষণিক)
        if (spec.uri.host != "h") {
            app.offlineStore.localFile(accountId, sources[index])?.let { return@Resolver spec.withUri(Uri.fromFile(it)) }
        }
        val url = try {
            if (spec.uri.host == "h") {
                resolvedHls.getOrPut(index) {
                    runBlocking { app.rcloneApi.hlsUrl(fsSpec, sources[index], HLS_QUALITY) }
                }
            } else {
                resolved.getOrPut(index) {
                    runBlocking { app.rcloneApi.streamUrl(fsSpec, sources[index], mimeFor(sources[index])) }
                }
            }
        } catch (t: Throwable) {
            throw IOException(t.message ?: t.toString(), t)
        }
        spec.withUri(Uri.parse(url))
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            // HLS চালু না হলে মূল ফাইলে ফিরে যাই (ধীর হলেও চলে)
            val at = player.currentMediaItemIndex
            val entry = items.getOrNull(at)
            if (entry != null && hlsPaths.remove(entry.path)) {
                val pos = player.currentPosition
                player.replaceMediaItem(at, buildItem(entry, siblingList, hls = false, replaceAt = at))
                player.prepare()
                player.seekTo(at, pos)
                player.playWhenReady = true
                return
            }
            _error.value = error.cause?.message ?: error.message ?: error.errorCodeName
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // আগের ফাইল শেষ পর্যন্ত চলে থাকলে তার সংরক্ষিত অবস্থান মুছে ফেলা
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                items.getOrNull(lastIndex)?.let { forget(it.path) }
            }
            lastIndex = player.currentMediaItemIndex
            _error.value = null
            updateKind()
            prewarm(lastIndex + 1)
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                val entry = items.getOrNull(lastIndex) ?: return
                val saved = positions.getLong(key(entry.path), 0L)
                if (saved > MIN_RESUME_MS) player.seekTo(saved)
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            stallJob?.cancel()
            if (state == Player.STATE_BUFFERING && player.playWhenReady) {
                // অনেকক্ষণ শুধু লোডিং: অন্তহীন ঘুরতে না দিয়ে আসল কারণ (সার্ভারের লগ) দেখানো
                val idx = player.currentMediaItemIndex
                stallJob = scope.launch {
                    delay(STALL_MS)
                    if (player.playbackState == Player.STATE_BUFFERING && player.currentMediaItemIndex == idx) {
                        val why = com.drivemount.app.engine.StreamServer.diagnostics()
                        _error.value = app.getString(com.drivemount.app.R.string.player_stalled) +
                            if (why.isNotBlank()) "\n\n$why" else ""
                    }
                }
            }
            if (state == Player.STATE_READY) _error.value = null
            if (state == Player.STATE_ENDED) items.getOrNull(player.currentMediaItemIndex)?.let { forget(it.path) }
            syncService()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) savePosition()
            syncService()
        }
    }

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(app, DefaultRenderersFactory(app).setEnableDecoderFallback(true))
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    ResolvingDataSource.Factory(
                        androidx.media3.datasource.DefaultDataSource.Factory(
                            app,
                        DefaultHttpDataSource.Factory()
                            .setConnectTimeoutMs(15_000)
                            .setReadTimeoutMs(30_000)
                            // Terabox HLS-এর টুকরোগুলো সরাসরি তাদের সার্ভার থেকে আসে; এই হেডার লাগে (TeraPlay-এর মতো)
                            .setUserAgent("terabox;1.40.0.132;PC;PC-Windows;10.0.26100;WindowsTeraBox")
                            .setAllowCrossProtocolRedirects(true)
                            .setDefaultRequestProperties(mapOf("Referer" to "https://www.terabox.com/")),
                        ),
                        resolver,
                    ),
                ),
            )
            .setLoadControl(
                // বড় buffer, কিন্তু মেমরির সীমা সহ (সময় আগে ধরলে উচ্চ-বিটরেট ভিডিওে মেমরি ফুরিয়ে GC-তে আটকে যেত)।
                // শুরু ২ সেকেন্ডে, আটকে গেলে ৫ সেকেন্ড জমিয়ে তবে চলে: ঘনঘন থেমে-চলা কমে।
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(40_000, 150_000, 2_000, 5_000)
                    .setTargetBufferBytes(64 * 1024 * 1024)
                    .setPrioritizeTimeOverSizeThresholds(false)
                    .build(),
            )
            // false = অন্য অ্যাপ অডিও নিলেও চলতে থাকে (PlaybackService.PLAY_THROUGH_INTERRUPTIONS)
            .setAudioAttributes(audioAttributes(music = false), !PlaybackService.PLAY_THROUGH_INTERRUPTIONS)
            .setHandleAudioBecomingNoisy(true)
            // স্ক্রিন বন্ধ থাকলেও CPU ও Wi-Fi জাগিয়ে রাখে, যাতে স্ট্রিমিং না থামে
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .also {
                it.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                it.addListener(listener)
            }
    }

    // ---------------- খোলা, ছোট-বড় করা, বন্ধ করা ----------------

    /** ফাইল চালানো শুরু করে বড় (YouTube-স্টাইল) প্লেয়ারে খোলে। একই গান চলতে থাকলে শুধু বড় করে */
    fun open(accountId: Long, path: String) {
        val current = items.getOrNull(player.currentMediaItemIndex)
        val same = this.accountId == accountId && current?.path == path && player.mediaItemCount > 0
        if (!same) load(accountId, path)
        _fullscreen.value = false
        _mode.value = Mode.EXPANDED
    }

    /** TeraPlay-র একটি ভিডিও: [loader] চালানোর সময় Terabox-এর HLS তালিকা (টেক্সট) এনে দেয় (ব্যাকগ্রাউন্ড থ্রেডে ডাকা হয়) */
    class TeraPlayItem(val id: String, val title: String, val loader: () -> String)

    /**
     * TeraPlay প্লাগইন: তালিকার ভিডিও চালায় (rclone/অ্যাকাউন্ট ছাড়াই)। প্রতিটি ভিডিওর HLS তালিকা
     * চালানোর মুহূর্তে আনা হয়, তাই পরের ভিডিও নিজে থেকেই চলে। [id] স্থির থাকে বলে শেষ অবস্থান মনে থাকে।
     */
    fun openTeraPlay(
        list: List<TeraPlayItem>,
        start: Int,
        accId: Long = TERAPLAY_ACCOUNT,
        acc: AccountEntity? = null,
        pathPrefix: String = "teraplay:",
    ) {
        if (list.isEmpty()) return
        leaveBackground()
        savePosition()
        muteSave = true
        player.stop()
        player.clearMediaItems()
        accountId = accId
        _account.value = acc
        itemSource.clear()
        hlsPaths.clear()
        siblingList = emptyList()
        lastIndex = start
        _error.value = null
        _isAudio.value = false

        java.io.File(app.cacheDir, "teraplay").also { it.mkdirs() }.listFiles()?.forEach { it.delete() }
        tpLoaders.clear()
        tpResolved.clear()

        _queue.value = list.map {
            RemoteEntry(name = it.title, path = "$pathPrefix${it.id}", isDir = false, size = null, modTimeMillis = null, mimeType = "")
        }
        val items = list.map { item ->
            tpLoaders.add(item.loader)
            MediaItem.Builder()
                .setMediaId("$pathPrefix${item.id}")
                .setUri("tp://h/${tpLoaders.lastIndex}")
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title.substringBeforeLast('.', item.title)).build())
                .build()
        }
        val s = start.coerceIn(0, list.lastIndex)
        val saved = positions.getLong(key("$pathPrefix${list[s].id}"), 0L)
        player.setMediaItems(items, s, if (saved > MIN_RESUME_MS) saved else C.TIME_UNSET)
        muteSave = false
        player.prepare()
        player.playWhenReady = true
        _fullscreen.value = false
        _mode.value = Mode.EXPANDED
    }

    fun expand() {
        if (_mode.value != Mode.HIDDEN) _mode.value = Mode.EXPANDED
    }

    fun minimize() {
        if (_mode.value == Mode.EXPANDED) {
            _fullscreen.value = false
            _mode.value = Mode.MINI
        }
    }

    fun setFullscreen(on: Boolean) {
        _fullscreen.value = on && _mode.value == Mode.EXPANDED
    }

    /** একদম বন্ধ: থামে, তালিকা মোছে, মিনি প্লেয়ারও চলে যায় */
    fun close() {
        savePosition()
        leaveBackground()
        loadJob?.cancel()
        muteSave = true
        player.stop()
        player.clearMediaItems()
        muteSave = false
        _queue.value = emptyList()
        itemSource.clear()
        hlsPaths.clear()
        lastIndex = -1
        _error.value = null
        _fullscreen.value = false
        _isAudio.value = false
        _mode.value = Mode.HIDDEN
        syncService()
    }

    private fun load(accId: Long, path: String) {
        loadJob?.cancel()
        leaveBackground()
        savePosition()
        muteSave = true
        player.stop()
        player.clearMediaItems()
        accountId = accId
        _queue.value = emptyList()
        itemSource.clear()
        hlsPaths.clear()
        siblingList = emptyList()
        lastIndex = -1
        _error.value = null
        // ফাইল খোলার আগেই গান/ভিডিও বোঝা গেলে সঠিক পর্দা সঙ্গে সঙ্গে দেখানো যায়
        _isAudio.value = mimeFor(path).startsWith("audio/")

        loadJob = scope.launch {
            try {
                val acc = app.accountRepository.getById(accId)
                    ?: throw IllegalStateException("Account not found")
                _account.value = acc
                fsSpec = fsRootFor(acc.remoteName, acc.backendType)
                useHls = acc.backendType == "terabox" && StreamServer.isAvailable()

                // "Terabox (TeraPlay)" দিয়ে যোগ করা অ্যাকাউন্টের ভিডিও TeraPlay-র দ্রুত ইঞ্জিনে চলে
                val tp = if (acc.backendType == "terabox" && mimeFor(path).startsWith("video/")) {
                    withContext(Dispatchers.IO) { TpAccountStore(app).load().firstOrNull { it.id == accId.toString() } }
                } else null
                if (tp != null) {
                    val q = PlaybackQueue.peek(accId)?.takeIf { qq -> qq.items.any { it.path == path } }
                    val videos = (q?.items ?: listOf(RemoteEntry(path.substringAfterLast('/'), path, false, null, null, "")))
                        .filter { mimeFor(it.path).startsWith("video/") }
                    val tpItems = videos.map { v ->
                        TeraPlayItem(v.path, v.name) {
                            TeraBoxClient(tp.cookie) { }
                                .streamPath("/" + v.path.trimStart('/'), TeraBoxClient.STREAM_TYPE).m3u8
                        }
                    }
                    muteSave = false
                    openTeraPlay(tpItems, videos.indexOfFirst { it.path == path }.coerceAtLeast(0), accId, acc, "")
                    return@launch
                }

                val queue = PlaybackQueue.peek(accId)?.takeIf { q -> q.items.any { it.path == path } }
                val list = queue?.items ?: listOf(
                    RemoteEntry(
                        name = path.substringAfterLast('/'),
                        path = path,
                        isDir = false,
                        size = null,
                        modTimeMillis = null,
                        mimeType = "",
                    ),
                )
                _queue.value = list
                val siblings = queue?.siblings.orEmpty()
                siblingList = siblings
                val startIndex = list.indexOfFirst { it.path == path }.coerceAtLeast(0)

                val mediaItems = list.map { buildItem(it, siblings, hls = useHls) }
                lastIndex = startIndex
                val saved = positions.getLong(key(list[startIndex].path), 0L)
                player.setMediaItems(mediaItems, startIndex, if (saved > MIN_RESUME_MS) saved else C.TIME_UNSET)
                muteSave = false
                player.prepare()
                player.playWhenReady = true
                updateKind()
                prewarm(startIndex + 1)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                muteSave = false
                _error.value = t.message ?: t.toString()
            }
        }
    }

    // ---------------- গান / ভিডিও ধরন ও ব্যাকগ্রাউন্ড সার্ভিস ----------------

    private fun updateKind() {
        val entry = items.getOrNull(player.currentMediaItemIndex) ?: return
        val audio = mimeFor(entry.path).startsWith("audio/")
        if (_isAudio.value != audio) {
            _isAudio.value = audio
            player.setAudioAttributes(audioAttributes(music = audio), !PlaybackService.PLAY_THROUGH_INTERRUPTIONS)
        }
        syncService()
    }

    /**
     * কিছু চালু থাকলে (গান বা ভিডিও) ফোরগ্রাউন্ড সার্ভিস + MediaSession চালু রাখে: অ্যাপ থেকে বেরোলে বা স্ক্রিন বন্ধ করলেও
     * প্লেব্যাক চলে, আর নোটিফিকেশন/লক-স্ক্রিনে কন্ট্রোল থাকে। প্লেয়ার বন্ধ (close) হলে সার্ভিসও বন্ধ।
     */
    private fun syncService() {
        val keep = _mode.value != Mode.HIDDEN
        val running = PlaybackService.running
        val state = player.playbackState
        // সার্ভিস শুরুর ৫ সেকেন্ডের মধ্যে নোটিফিকেশন দেখাতে হয়, তাই প্লেয়ার প্রস্তুত/লোড হওয়ার পরেই শুরু করি
        val active = state == Player.STATE_READY || state == Player.STATE_BUFFERING
        if (keep && !running && active && player.playWhenReady) {
            runCatching {
                ContextCompat.startForegroundService(app, Intent(app, PlaybackService::class.java))
            }
        } else if (!keep && running) {
            runCatching { app.stopService(Intent(app, PlaybackService::class.java)) }
        }
    }

    // ---------------- ব্যাকগ্রাউন্ড ----------------

    private var videoDisabled = false

    /**
     * অ্যাপ আড়ালে গেলে ভিডিওর ছবির অংশ বন্ধ (ব্যাটারি বাঁচে), শব্দ চলতে থাকে।
     * (আগে ভিডিও থেমে যেত।) ফিরে এলে [leaveBackground] ছবি আবার চালু করে।
     */
    fun enterBackground() {
        if (_isAudio.value || videoDisabled || _mode.value == Mode.HIDDEN) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
        videoDisabled = true
    }

    fun leaveBackground() {
        if (!videoDisabled) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .build()
        videoDisabled = false
    }

    private fun audioAttributes(music: Boolean) = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(if (music) C.AUDIO_CONTENT_TYPE_MUSIC else C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    // ---------------- লিংক তৈরি ----------------

    /** পরের ফাইলের লিংক আগেই বানিয়ে রাখে, যাতে পালা এলে অপেক্ষা না করতে হয় */
    private fun prewarm(mediaIndex: Int) {
        val src = itemSource.getOrNull(mediaIndex) ?: return
        if (app.offlineStore.localFile(accountId, sources[src]) != null) return
        val hls = hlsPaths.contains(sources[src])
        if (if (hls) resolvedHls.containsKey(src) else resolved.containsKey(src)) return
        scope.launch {
            runCatching {
                if (hls) {
                    resolvedHls.putIfAbsent(src, app.rcloneApi.hlsUrl(fsSpec, sources[src], HLS_QUALITY))
                } else {
                    val url = app.rcloneApi.streamUrl(fsSpec, sources[src], mimeFor(sources[src]))
                    resolved.putIfAbsent(src, url)
                    // পরের গান/ভিডিওর শুরু ও শেষের অংশ আগেভাগে ক্যাশে: পালা এলে বিরতি থাকে না
                    val mime = mimeFor(sources[src])
                    if (mime.startsWith("audio/") || mime.startsWith("video/")) {
                        StreamWarmer.warmUrl(app, "$accountId|${sources[src]}", url, mime.startsWith("audio/"))
                    }
                }
            }
        }
    }

    private fun mimeFor(path: String): String {
        val m = guessMime(path.substringAfterLast('/'))
        return if (m == "*/*") "" else m
    }

    private fun register(path: String): Int {
        sources.add(path)
        return sources.lastIndex
    }

    private fun buildItem(
        entry: RemoteEntry,
        siblings: List<RemoteEntry>,
        hls: Boolean,
        replaceAt: Int = -1,
    ): MediaItem {
        val index = register(entry.path)
        if (replaceAt >= 0) itemSource[replaceAt] = index else itemSource.add(index)
        val base = entry.name.substringBeforeLast('.', entry.name)
        var firstSub = true
        val subs = siblings
            .filter { !it.isDir && it.name.startsWith("$base.", ignoreCase = true) }
            .mapNotNull { sib ->
                val ext = sib.name.substringAfterLast('.', "").lowercase()
                val mime = when (ext) {
                    "srt" -> MimeTypes.APPLICATION_SUBRIP
                    "vtt" -> MimeTypes.TEXT_VTT
                    "ass", "ssa" -> MimeTypes.TEXT_SSA
                    else -> return@mapNotNull null
                }
                // "movie.en.srt" → ভাষা "en"
                val lang = sib.name.substring(base.length + 1).substringBeforeLast('.', "").ifBlank { null }
                MediaItem.SubtitleConfiguration.Builder(Uri.parse("dm://f/${register(sib.path)}"))
                    .setMimeType(mime)
                    .setLanguage(lang ?: "und")
                    .setLabel(lang ?: sib.name)
                    // পাশের সাবটাইটেল ফাইল থাকলে প্রথমটি নিজে থেকেই চালু হয়
                    .setSelectionFlags(if (firstSub) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
                    .also { firstSub = false }
            }
        val mime = mimeFor(entry.path)
        val asHls = hls && mime.startsWith("video/")
        if (asHls) hlsPaths.add(entry.path)
        return MediaItem.Builder()
            .setMediaId(entry.path)
            .setUri(if (asHls) "dm://h/$index" else "dm://f/$index")
            .apply {
                if (asHls) setMimeType(MimeTypes.APPLICATION_M3U8) else if (mime.isNotEmpty()) setMimeType(mime)
            }
            .setSubtitleConfigurations(subs)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(base).build())
            .build()
    }

    // ---------------- অবস্থান মনে রাখা ----------------

    private fun key(path: String) = "$accountId|$path"

    private fun forget(path: String) {
        positions.edit().remove(key(path)).apply()
    }

    /** চলা অংশ সংরক্ষণ: শেষের ১০ সেকেন্ডে বা শুরুর ৫ সেকেন্ডে থাকলে মুছে ফেলা হয় */
    fun savePosition() {
        if (muteSave) return
        val entry = items.getOrNull(player.currentMediaItemIndex) ?: return
        val pos = player.currentPosition
        val dur = player.duration
        if (dur == C.TIME_UNSET || pos < MIN_RESUME_MS || pos > dur - 10_000) {
            forget(entry.path)
        } else {
            positions.edit().putLong(key(entry.path), pos).apply()
        }
    }

    // ---------------- বাইরের প্লেয়ার, আবার চেষ্টা ----------------

    /** অন্য প্লেয়ার অ্যাপে খুলতে বর্তমান ফাইলের লোকাল লিংক */
    suspend fun externalUrl(): Pair<String, String>? {
        val i = player.currentMediaItemIndex
        val src = itemSource.getOrNull(i) ?: return null
        val url = resolved[src] ?: app.rcloneApi.streamUrl(fsSpec, sources[src], mimeFor(sources[src]))
            .also { resolved[src] = it }
        return url to mimeFor(sources[src]).ifEmpty { "video/*" }
    }

    fun retry() {
        tpResolved.clear()
        _error.value = null
        player.prepare()
        player.play()
    }

    private companion object {
        const val MIN_RESUME_MS = 5_000L
        private const val STALL_MS = 60_000L
        const val TERAPLAY_ACCOUNT = -2L

        /** Terabox-এর স্ট্রিম মান; ৪৮০p = দ্রুত চালু (TeraPlay-এর মতো)। উঁচু মান: M3U8_AUTO_720 / M3U8_AUTO_1080 */
        const val HLS_QUALITY = "M3U8_AUTO_480"
    }
}

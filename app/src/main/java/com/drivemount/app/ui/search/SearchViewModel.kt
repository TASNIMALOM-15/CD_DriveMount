package com.drivemount.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivemount.app.DriveMountApp
import com.drivemount.app.engine.RemoteEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** একটি খোঁজার ফল: কোন অ্যাকাউন্টের কোন ফোল্ডারে (parent) */
data class SearchHit(
    val accountId: Long,
    val accountName: String,
    val entry: RemoteEntry,
    /** ফাইলটি যে ফোল্ডারে আছে (root হলে খালি) */
    val parent: String,
)

/** forQuery: এই ফল কোন লেখার জন্য (পর্দার লেখার সঙ্গে না মিললে ফল এখনো আসেনি) */
data class SearchState(val forQuery: String = "", val hits: List<SearchHit> = emptyList())

/**
 * সব ক্লাউডে একসঙ্গে খোঁজা। খোঁজা হয় লোকাল ইনডেক্সে (নেটওয়ার্ক লাগে না, অফলাইনেও চলে);
 * তাই শুধু সেই ফোল্ডারগুলো পাওয়া যায় যেগুলো অ্যাপে আগে একবার খোলা হয়েছে।
 * টাইপ থামার ২৫০ মিলিসেকেন্ড পরে খোঁজা শুরু হয় এবং নতুন অক্ষর এলে আগেরটা বাতিল হয়।
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = app as DriveMountApp

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun setQuery(text: String) {
        _query.value = text
    }

    val state: StateFlow<SearchState> = _query
        .debounce(250)
        .mapLatest { raw ->
            if (raw.isBlank()) {
                SearchState(raw)
            } else {
                val rows = appRef.fileRepository.search(raw)
                val accounts = appRef.accountRepository.getAll()
                val names = accounts.associate { it.id to it.displayName }
                val vaultHits = appRef.fileRepository.searchVaults(raw, accounts.filter { it.backendType == "crypt" }.map { it.id })
                    .mapNotNull { (id, entry, parent) ->
                        SearchHit(id, names[id] ?: return@mapNotNull null, entry, parent)
                    }
                SearchState(
                    forQuery = raw,
                    // মুছে ফেলা অ্যাকাউন্টের পুরোনো সারি বাদ
                    hits = vaultHits + rows.mapNotNull { r ->
                        val name = names[r.accountId] ?: return@mapNotNull null
                        SearchHit(
                            accountId = r.accountId,
                            accountName = name,
                            entry = RemoteEntry(
                                name = r.name,
                                path = r.path,
                                isDir = r.isDir,
                                size = r.size.takeIf { it >= 0 },
                                modTimeMillis = r.modTime.takeIf { it >= 0 },
                                mimeType = r.mime,
                            ),
                            parent = r.parent,
                        )
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())
}

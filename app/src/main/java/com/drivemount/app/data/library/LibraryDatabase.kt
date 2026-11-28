package com.drivemount.app.data.library

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Kinds used by the library tabs */
object MediaKind {
    const val VIDEO = 0
    const val PHOTO = 1
    const val AUDIO = 2
}

/**
 * Per-file user marks. `itemKey` = "accountId|path". favorite/hidden/locked are independent flags
 * (hidden/locked are reserved for the "Private" area, built in a later step).
 */
@Entity(tableName = "media_mark", indices = [Index("kind")])
data class MediaMark(
    @PrimaryKey @ColumnInfo(name = "itemKey") val itemKey: String,
    val kind: Int,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val updatedAt: Long = 0L,
)

/** Schema reserved for playlists (UI comes in a later step) */
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Int,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "playlist_item", primaryKeys = ["playlistId", "position"], indices = [Index("playlistId"), Index("itemKey")])
data class PlaylistItemEntity(
    val playlistId: Long,
    val position: Int,
    @ColumnInfo(name = "itemKey") val itemKey: String,
)

/** A playlist with its track count (for lists of hundreds/thousands of playlists) */
data class PlaylistRow(val id: Long, val name: String, val count: Int, val coverKey: String?)

@Dao
interface LibraryDao {
    @Query("SELECT p.id AS id, p.name AS name, COUNT(i.itemKey) AS count, (SELECT itemKey FROM playlist_item WHERE playlistId = p.id ORDER BY position LIMIT 1) AS coverKey FROM playlist p LEFT JOIN playlist_item i ON i.playlistId = p.id WHERE p.kind = :kind GROUP BY p.id ORDER BY p.name COLLATE NOCASE")
    fun playlists(kind: Int): kotlinx.coroutines.flow.Flow<List<PlaylistRow>>

    @Query("SELECT itemKey FROM playlist_item WHERE playlistId = :id ORDER BY position")
    fun playlistItems(id: Long): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("SELECT itemKey FROM playlist_item WHERE playlistId = :id")
    suspend fun playlistKeys(id: Long): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_item WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int

    @Insert
    suspend fun insertPlaylist(p: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(list: List<PlaylistItemEntity>)

    @Query("UPDATE playlist SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_item WHERE playlistId = :id")
    suspend fun deletePlaylistItems(id: Long)

    @Query("DELETE FROM playlist_item WHERE playlistId = :id AND itemKey = :k")
    suspend fun removePlaylistItem(id: Long, k: String)

    @Query("SELECT itemKey FROM media_mark WHERE favorite = 1")
    fun favoriteKeys(): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("SELECT itemKey FROM media_mark WHERE hidden = 1")
    fun hiddenKeys(): kotlinx.coroutines.flow.Flow<List<String>>

    @Query("SELECT * FROM media_mark WHERE itemKey = :k")
    suspend fun get(k: String): MediaMark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(m: MediaMark)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(list: List<MediaMark>)
}

/** User data (favorites, later playlists/hidden): never destructive — unlike the cache databases */
@Database(entities = [MediaMark::class, PlaylistEntity::class, PlaylistItemEntity::class], version = 1, exportSchema = false)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao
}

class MediaMarks(context: Context) {
    private val db = Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "library.db").build()
    private val dao = db.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** All favorite keys ("accountId|path") across Videos/Photos/Audios */
    val favorites: StateFlow<Set<String>> = dao.favoriteKeys().map { it.toHashSet() as Set<String> }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Keys moved to the Private area (hidden from every other view) */
    val hidden: StateFlow<Set<String>> = dao.hiddenKeys().map { it.toHashSet() as Set<String> }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    suspend fun setHidden(items: List<Pair<String, Int>>, hide: Boolean) {
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.putAll(items.map { (k, kind) -> (dao.get(k) ?: MediaMark(k, kind)).copy(hidden = hide, updatedAt = now) })
    }

    /** If every item is already a favorite, un-favorites them all; otherwise favorites them all */
    suspend fun toggleFavorites(items: List<Pair<String, Int>>) {
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        val allFav = items.all { it.first in favorites.value }
        dao.putAll(items.map { (k, kind) ->
            (dao.get(k) ?: MediaMark(k, kind)).copy(favorite = !allFav, updatedAt = now)
        })
    }

    // ---- playlists ----
    fun playlists(kind: Int) = dao.playlists(kind)
    fun playlistItems(id: Long) = dao.playlistItems(id)

    suspend fun createPlaylist(kind: Int, name: String): Long =
        dao.insertPlaylist(PlaylistEntity(kind = kind, name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, name.trim())

    suspend fun deletePlaylist(id: Long) { dao.deletePlaylistItems(id); dao.deletePlaylist(id) }

    /** Appends the tracks (already-present ones are skipped, order kept) */
    suspend fun addToPlaylist(id: Long, keys: List<String>) {
        val have = dao.playlistKeys(id).toHashSet()
        var pos = dao.maxPosition(id)
        dao.insertItems(keys.distinct().filter { it !in have }.map { PlaylistItemEntity(id, ++pos, it) })
    }

    suspend fun removeFromPlaylist(id: Long, key: String) = dao.removePlaylistItem(id, key)
}

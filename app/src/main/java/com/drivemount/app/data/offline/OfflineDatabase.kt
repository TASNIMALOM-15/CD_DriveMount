package com.drivemount.app.data.offline

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase

/** ব্যবহারকারী "অফলাইনে রাখো" চেপেছে এমন ফাইল/ফোল্ডার */
@Entity(tableName = "offline_pin", primaryKeys = ["accountId", "path"])
data class OfflinePin(
    val accountId: Long,
    val path: String,
    val isDir: Boolean,
    val name: String,
    val size: Long?,
    val modTime: Long?,
)

/** ফোনে নামানো সম্পূর্ণ ফাইল (ফোল্ডার পিন করলে তার ভেতরের প্রতিটি ফাইল আলাদা সারি) */
@Entity(tableName = "offline_file", primaryKeys = ["accountId", "path"])
data class OfflineFile(
    val accountId: Long,
    val path: String,
    val size: Long,
    val modTime: Long?,
    /** অফলাইন ফোল্ডারের ভেতরের আপেক্ষিক পথ: `<hash>/<name>` */
    val local: String,
)

@Dao
interface OfflineDao {
    @Query("SELECT * FROM offline_pin")
    suspend fun pins(): List<OfflinePin>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPin(pin: OfflinePin)

    @Query("DELETE FROM offline_pin WHERE accountId = :accountId AND path = :path")
    suspend fun deletePin(accountId: Long, path: String)

    @Query("SELECT * FROM offline_file")
    suspend fun files(): List<OfflineFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: OfflineFile)

    @Query("DELETE FROM offline_file WHERE accountId = :accountId AND path = :path")
    suspend fun deleteFile(accountId: Long, path: String)

    @Query("DELETE FROM offline_file")
    suspend fun deleteAllFiles()

    @Query("DELETE FROM offline_pin")
    suspend fun deleteAllPins()
}

@Database(entities = [OfflinePin::class, OfflineFile::class], version = 1, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao
}

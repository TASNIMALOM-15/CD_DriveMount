package com.drivemount.app.data.transfer

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

object SyncMode {
    /** সোর্স থেকে গন্তব্যে নতুন/বদলানো ফাইল কপি; কিছু মোছে না */
    const val COPY = "COPY"

    /** গন্তব্যকে সোর্সের হুবহু প্রতিলিপি করে; সোর্সে নেই এমন ফাইল গন্তব্য থেকে মুছে যায় */
    const val MIRROR = "MIRROR"

    /** সোর্স থেকে গন্তব্যে সরায়; সোর্স থেকে ফাইল মুছে যায় */
    const val MOVE = "MOVE"
}

/** দুটি ফোল্ডারের মধ্যে একমুখী sync-এর সংরক্ষিত সেটিং */
@Entity(tableName = "sync_profiles")
data class SyncProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val srcAccountId: Long,
    val srcPath: String,
    val dstAccountId: Long,
    val dstPath: String,
    val mode: String = SyncMode.COPY,
    /** 0 = শুধু হাতে চালানো; নইলে এত মিনিট পরপর স্বয়ংক্রিয় */
    val intervalMinutes: Int = 0,
    val lastRunAt: Long? = null,
    val lastStatus: String? = null,
    val lastMessage: String? = null,
    val createdAt: Long,
)

@Dao
interface SyncProfileDao {

    @Query("SELECT * FROM sync_profiles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SyncProfileEntity>>

    @Query("SELECT * FROM sync_profiles WHERE id = :id")
    suspend fun getById(id: Long): SyncProfileEntity?

    @Insert
    suspend fun insert(profile: SyncProfileEntity): Long

    @Update
    suspend fun update(profile: SyncProfileEntity)

    @Query("DELETE FROM sync_profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sync_profiles SET lastRunAt = :at, lastStatus = :status, lastMessage = :message WHERE id = :id")
    suspend fun setResult(id: Long, at: Long, status: String, message: String?)
}

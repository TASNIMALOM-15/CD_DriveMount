package com.drivemount.app.data.transfer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {

    @Query("SELECT * FROM transfers ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT COUNT(*) FROM transfers WHERE status IN ('QUEUED','PREPARING','RUNNING')")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transfers WHERE status IN ('QUEUED','PREPARING','RUNNING')")
    suspend fun countPending(): Int

    @Query("SELECT * FROM transfers WHERE status = 'QUEUED' ORDER BY createdAt ASC, id ASC LIMIT 1")
    suspend fun nextQueued(): TransferEntity?

    @Query("SELECT COUNT(*) FROM transfers WHERE profileId = :profileId AND status IN ('QUEUED','PREPARING','RUNNING')")
    suspend fun countActiveForProfile(profileId: Long): Int

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun getById(id: Long): TransferEntity?

    @Insert
    suspend fun insert(task: TransferEntity): Long

    @Query("UPDATE transfers SET status = :status, error = :error, finishedAt = :finishedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String?, finishedAt: Long?)

    @Query(
        "UPDATE transfers SET status = 'DONE', error = NULL, finishedAt = :at, " +
            "bytesDone = CASE WHEN bytesTotal > 0 THEN bytesTotal ELSE bytesDone END WHERE id = :id",
    )
    suspend fun setDone(id: Long, at: Long)

    @Query("UPDATE transfers SET bytesDone = :done, bytesTotal = :total, speed = :speed WHERE id = :id")
    suspend fun updateProgress(id: Long, done: Long, total: Long, speed: Long)

    /** অ্যাপ হঠাৎ বন্ধ হলে আটকে থাকা কাজ আবার লাইনে ফেরানো */
    @Query("UPDATE transfers SET status = 'QUEUED' WHERE status IN ('PREPARING','RUNNING')")
    suspend fun resetRunning()

    @Query("UPDATE transfers SET status = 'QUEUED', error = NULL, finishedAt = NULL, bytesDone = 0, speed = 0 WHERE id = :id")
    suspend fun requeue(id: Long)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transfers WHERE status IN ('DONE','FAILED','CANCELLED')")
    suspend fun deleteFinished()
}

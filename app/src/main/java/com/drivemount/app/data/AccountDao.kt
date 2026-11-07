package com.drivemount.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Query("UPDATE accounts SET displayName = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE accounts SET usedBytes = :used, totalBytes = :total, usageUpdatedAt = :at WHERE id = :id")
    suspend fun updateUsage(id: Long, used: Long?, total: Long?, at: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}

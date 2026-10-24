package com.drivemount.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * অ্যাপের অ্যাকাউন্ট তালিকার তথ্য। গোপন তথ্য (token/password) এখানে থাকে না,
 * সেগুলো rclone.conf-এ থাকে। remoteName হলো rclone-এর ভেতরের নাম, displayName ব্যবহারকারীর দেখা নাম।
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["remoteName"], unique = true)],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteName: String,
    val displayName: String,
    val backendType: String,
    val createdAt: Long,
    val sortOrder: Int = 0,
    val usedBytes: Long? = null,
    val totalBytes: Long? = null,
    val usageUpdatedAt: Long? = null,
)

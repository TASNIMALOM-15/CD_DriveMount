package com.drivemount.app.data.transfer

import androidx.room.Entity
import androidx.room.PrimaryKey

object TransferStatus {
    const val QUEUED = "QUEUED"
    const val PREPARING = "PREPARING"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

/**
 * একটি ফাইল বা ফোল্ডারের কপি/সরানোর কাজ। srcFs/dstFs হলো rclone-এর "fs" (যেমন "dmabc:" বা "/storage/...")।
 * ফোল্ডারের ক্ষেত্রে পথ মিলিয়ে (fsJoin) পুরো fs বানানো হয়।
 */
@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDir: Boolean,
    val move: Boolean,
    val srcFs: String,
    val srcPath: String,
    val dstFs: String,
    val dstPath: String,
    /** আপলোডের জন্য বাছাই করা ফাইলের content URI; কাজ শুরুর আগে অস্থায়ী কপি বানানো হয় */
    val sourceUri: String? = null,
    /** শেষ হলে ফাইলটি খোলা হবে (ডাউনলোড করে দেখা) */
    val openWhenDone: Boolean = false,
    /** true হলে ফোল্ডার "mirror" হয়: গন্তব্যের বাড়তি ফাইল মুছে সোর্সের মতো করা হয় */
    val mirror: Boolean = false,
    /** কোনো sync প্রোফাইলের কাজ হলে তার id */
    val profileId: Long? = null,
    val status: String = TransferStatus.QUEUED,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val speed: Long = 0,
    val error: String? = null,
    val createdAt: Long,
    val finishedAt: Long? = null,
)

package com.drivemount.app.data.transfer

import androidx.room.Database
import androidx.room.RoomDatabase

/** ট্রান্সফারের তালিকা আলাদা ডাটাবেসে, যাতে অ্যাকাউন্টের ডাটাবেসে হাত দিতে না হয় */
@Database(entities = [TransferEntity::class, SyncProfileEntity::class], version = 2, exportSchema = false)
abstract class TransferDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao
    abstract fun syncProfileDao(): SyncProfileDao
}

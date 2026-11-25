package com.gullzubair.sarim.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedProfileEntity::class,
        KvStoreEntity::class,
        NdappOutboxEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SarimOfflineDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        @Volatile
        private var INSTANCE: SarimOfflineDatabase? = null

        fun getInstance(context: Context): SarimOfflineDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SarimOfflineDatabase::class.java,
                    "sarim_offline.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
